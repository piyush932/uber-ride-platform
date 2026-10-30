# High-Level Design

This document covers the whole target system, and says clearly which parts exist today.

## 1. Goal

Simulate the core flow of a ride-hailing app: a passenger requests a ride, nearby drivers are found, one driver is notified instantly, accepts, the ride is completed, and the passenger can review the driver. The point is to practise the distributed-systems problems behind that flow, not to clone a product.

## 2. What is built (Phase 1-7)

```mermaid
flowchart LR
    C[Client] -->|HTTP + JWT| GW[api-gateway :8080]
    GW -->|JWT check| A[auth-service :8081]
    GW --> B[booking-service :8084]
    GW --> L[location-service :8082]
    GW --> R[review-service :8085]
    B -->|nearest driver| L
    B -->|notify driver, circuit breaker| S[socket-service :8083]
    S -->|driver accepts| B
    R -->|is it COMPLETED?| B
    A --> E[eureka-server :8761]
    L --> E
    S --> E
    B --> E
    R --> E
    GW --> E
    A -->|JPA| M[(H2, or MySQL uber_auth)]
    B -->|JPA| M2[(H2, or MySQL uber_booking)]
    R -->|JPA| M3[(H2, or MySQL uber_review)]
    L --> ST[(in-memory store, or Redis GEO)]
    GW -. optional rate limit .-> RD[(Redis)]
    B -. events .-> EV[(log publisher, or Kafka)]
```

## 3. Target architecture (all phases)

```mermaid
flowchart TB
    UI[React frontend] --> GW[API gateway :8080]
    GW --> AUTH[auth-service]
    GW --> BOOK[booking-service]
    GW --> LOC[location-service]
    GW --> REV[review-service]
    BOOK -->|nearby drivers| LOC
    BOOK -->|notify driver| SOCK[socket-service]
    SOCK -->|WebSocket/STOMP| UI
    SOCK -->|driver accepts| BOOK
    BOOK -->|booking events| K[(Kafka)]
    K --> REV
    LOC --> R[(Redis GEO)]
    AUTH --> DB1[(DB)]
    BOOK --> DB2[(DB)]
    REV --> DB3[(DB)]
    AUTH & BOOK & LOC & SOCK & REV & GW --> EU[Eureka]
```

Phase 8 adds the React frontend. The final phase adds Docker Compose for every service with MySQL, Redis and Kafka.

## 4. Local mode and Docker mode

Every service has two ways to run, chosen by a Spring profile, so the same code is tested locally first and in Docker last:

| Concern | Local default | Real store (profile) | Used in |
|---------|---------------|----------------------|---------|
| Auth database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Booking database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Review database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Driver locations | In-memory store | Redis GEO, profile `redis` | local with Redis installed, Docker |
| Gateway rate limiting | Off | Redis, profile `redis` | local with Redis installed, Docker |
| Booking events | Logged by an in-process publisher | Kafka, profile `kafka` | Docker stage |
| Review trigger | Review-service calls booking-service | Kafka consumer of `BOOKING_COMPLETED` | Docker stage |
| Service discovery | Eureka on localhost | Eureka container | Docker |

The in-memory and real implementations sit behind the same interface (`LocationStore`, `BookingEventPublisher`), which is also a useful design point to explain in an interview.

## 5. Gateway flow (built)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as api-gateway
    participant F as JwtAuthFilter
    participant S as Service

    C->>GW: request with Bearer token
    GW->>F: filter
    alt public path (signup, login, health)
        F->>S: forward
    else valid token
        F->>F: verify signature + expiry
        F->>S: forward with X-User-Email
    else missing / bad / expired token
        F-->>C: 401 JSON
    end
    Note over GW,S: circuit breaker per route, JSON 503 fallback when open
```

Routes: `/api/v1/auth/**` to auth-service, `/api/v1/bookings/**` to booking-service, `/api/v1/locations/**` to location-service, `/api/v1/reviews/**` to review-service, all via Eureka (`lb://`). Socket-service REST is not exposed through the gateway; drivers use WebSocket and booking-service calls it internally.

## 6. Ride request flow (built)

```mermaid
sequenceDiagram
    participant P as Passenger
    participant GW as api-gateway
    participant B as booking-service
    participant L as location-service
    participant S as socket-service
    participant D as Driver app

    P->>GW: POST /bookings (Idempotency-Key)
    GW->>GW: JWT check
    GW->>B: forward
    B->>B: idempotency key seen? return same booking
    B->>B: save booking as PENDING
    B->>L: GET /nearby (5 km, nearest first, limit 1)
    L-->>B: nearest driver
    B->>S: POST /rides/notify (circuit breaker)
    S-->>D: STOMP push on /topic/drivers/{id}/rides
    D->>S: STOMP /app/rides/accept
    S->>B: POST /bookings/{id}/accept
    B->>B: PENDING to CONFIRMED (version check)
    B-->>S: 200 confirmed
    S-->>D: /topic/bookings/{id} CONFIRMED
    P->>GW: POST /bookings/{id}/complete
    GW->>B: forward
    B->>B: CONFIRMED to COMPLETED + BOOKING_COMPLETED event
```

If no driver is nearby the booking is saved as CANCELLED with reason `NO_DRIVERS_NEARBY` and the caller gets a 409. If socket-service is down, the circuit breaker opens after repeated failures and the booking stays PENDING. If location-service is down, the caller gets a 503.

## 7. Review flow (built)

```mermaid
sequenceDiagram
    participant P as Passenger
    participant GW as api-gateway
    participant R as review-service
    participant B as booking-service
    participant DB as review DB

    P->>GW: POST /reviews (bookingId, passengerId, rating)
    GW->>GW: JWT check
    GW->>R: forward
    R->>B: GET /bookings/{id}
    B-->>R: booking (status, passengerId, driverId)
    R->>R: booking COMPLETED? passenger matches? not already reviewed?
    R->>DB: insert review
    R-->>P: 201 review
```

Rules: one review per booking, only the booking's passenger can write it, rating must be 1-5, and the booking must be COMPLETED.

## 8. Data model

```mermaid
erDiagram
    PASSENGER ||--o{ BOOKING : requests
    DRIVER ||--o{ BOOKING : fulfils
    BOOKING ||--o| REVIEW : receives
    BOOKING ||--o{ IDEMPOTENCY_RECORD : keyed by
    PASSENGER { long id string name string email string password }
    DRIVER { long id string name string email string password }
    BOOKING { long id string status long passengerId long driverId double pickupLatitude double pickupLongitude long version }
    REVIEW { long id long bookingId long passengerId long driverId int rating string comment }
    IDEMPOTENCY_RECORD { long id string idempotencyKey long bookingId }
```

Booking references passengers and drivers by plain id, not by a foreign key, because they live in other services' databases. Booking status values: PENDING, CONFIRMED, COMPLETED, CANCELLED. The `version` column is for optimistic locking. Review has a unique constraint on `bookingId`. IdempotencyRecord has a unique constraint on `idempotencyKey`.

## 9. Decisions and trade-offs

| Decision | Why | Cost / what I'd change |
|----------|-----|------------------------|
| Stateless JWT instead of sessions | Any instance can verify a token, no shared session store | Tokens can't be revoked before expiry; a refresh-token flow would fix this |
| Gateway checks JWT | One place to stop unauthenticated traffic before it reaches services | Services should still defend themselves (defence in depth) |
| X-User-Email header downstream | Services can know who called without parsing the token again | A malicious direct call could fake it; in production the gateway port is the only public one |
| Circuit breakers per route | One slow service does not tie up the whole gateway | A fallback is generic; per-route fallbacks could be smarter |
| Resilience4j around booking -> socket | Socket being down does not hang or fail booking creation | The booking stays PENDING with no driver notified; a retry/re-dispatch flow is still missing |
| Idempotency key on booking creation | A retried request cannot create a second booking | The key is stored forever; production would add a TTL or cleanup job |
| Shared `entity-service` library | One definition of the core entities | Compile-time coupling; review-service owns its own entity instead |
| Database per service | Services can evolve schemas independently | No cross-service joins; services call each other or react to events |
| H2 and in-memory stores by default | Anyone can run the project with just a JDK | They behave differently from MySQL and Redis, so everything is re-tested against the real stores |
| Driver positions expire (TTL) | A driver whose app closed must not be offered rides | Redis uses a separate TTL key because GEO members can't expire individually |
| Plain WebSocket + STOMP with a simple in-memory broker | Easy to run and test | One socket-service instance only; scaling out needs an external broker |
| Review checks the booking over REST | Simple, and the rule stays in one place | A Kafka consumer would decouple the services; planned for Docker |
| Unique constraint on review.bookingId | The database itself stops duplicate reviews | A second insert fails; the service checks first for a friendlier 409 |
| Booking events behind an interface | Same code logs locally and publishes to Kafka in Docker | At-least-once delivery with no outbox: an event can be lost if the process dies between the DB write and the publish |
| `ddl-auto=update` | Fast to develop against | Not for production; I'd switch to Flyway migrations |
| Spring Boot 3.5.16 | Supports Java 17 to 25 | Boot 3.5 no longer gets free security patches; moving to Boot 4.x is a later upgrade |
| Maven multi-module build | One parent POM pins versions for every service | A change to the library rebuilds its dependants |

## 10. Known gaps

- No refresh tokens or email verification, and only passengers can log in so far (driver login is not built).
- Booking only tries the single nearest driver. There is no timeout, no re-dispatch to the next driver if they ignore the ride, and the chosen driver (`candidateDriverId`) is returned on create but not stored, so `accept` does not check that the accepting driver is the one who was notified.
- Booking driver ids must be numeric, while location accepts any string id.
- The idempotency key is stored forever; production would add a TTL or cleanup job.
- Review-service checks the booking synchronously over REST. If booking-service is down, reviews cannot be created (503). The Kafka-based flow is planned for the Docker stage.
- No integration tests against real Redis, MySQL or Kafka (Testcontainers is planned for the Docker stage); the Kafka publisher and consumer have not been run yet.
- The WebSocket flow is tested by hand in the browser page, not by an automated test.
- The auth service creates tables for every entity in the shared library, not just `passenger`.
- Default JWT secret in `application.properties` / `application.yml` is for local use only. Set `JWT_SECRET` anywhere else.
- On Windows PowerShell 5.1 the `health status is UP` line in some smoke tests reports a false failure (the Actuator content type comes back as bytes).

## 11. Roadmap

8. **React frontend**: booking form, live driver map (Leaflet + OpenStreetMap, free), login screen, ride status via WebSocket.
9. **Docker**: Compose for every service with MySQL, Redis and Kafka, a Kafka consumer in review-service, and a full Docker test guide.
