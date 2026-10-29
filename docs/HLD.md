# High-Level Design

This document covers the whole target system, and says clearly which parts exist today.

## 1. Goal

Simulate the core flow of a ride-hailing app: a passenger requests a ride, nearby drivers are found, one driver is notified instantly, accepts, the ride is completed, and the passenger can review the driver. The point is to practise the distributed-systems problems behind that flow, not to clone a product.

## 2. What is built (Phase 1-6)

```mermaid
flowchart LR
    C[Client: Postman / PowerShell / browser] -->|REST + JWT| A[auth-service :8081]
    C -->|REST| B[booking-service :8084]
    C -->|WebSocket| S[socket-service :8083]
    C -->|REST| R[review-service :8085]
    B -->|nearest driver| L[location-service :8082]
    B -->|notify driver| S
    S -->|driver accepts| B
    R -->|is it COMPLETED?| B
    A --> E[eureka-server :8761]
    L --> E
    S --> E
    B --> E
    R --> E
    A -->|JPA| M[(H2, or MySQL uber_auth)]
    B -->|JPA| M2[(H2, or MySQL uber_booking)]
    R -->|JPA| M3[(H2, or MySQL uber_review)]
    L --> ST[(in-memory store, or Redis GEO)]
    B -. events .-> EV[(log publisher, or Kafka)]
    A -. uses .-> LIB[entity-service library]
    B -. uses .-> LIB
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

Phase 7-8 add the gateway and the frontend. Each service has its own database or store.

## 4. Local mode and Docker mode

Every service has two ways to run, chosen by a Spring profile, so the same code is tested locally first and in Docker last:

| Concern | Local default | Real store (profile) | Used in |
|---------|---------------|----------------------|---------|
| Auth database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Booking database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Review database | H2 in memory | MySQL, profile `mysql` | local with MySQL installed, Docker |
| Driver locations | In-memory store | Redis GEO, profile `redis` | local with Redis installed, Docker |
| Booking events | Logged by an in-process publisher | Kafka, profile `kafka` | Docker stage |
| Review trigger | Review-service calls booking-service | Kafka consumer of `BOOKING_COMPLETED` | Docker stage |
| Service discovery | Eureka on localhost | Eureka container | Docker |

The in-memory and real implementations sit behind the same interface (`LocationStore`, `BookingEventPublisher`), which is also a useful design point to explain in an interview. Driver positions stay in a memory-class store (Redis) even in production, because they change every few seconds and expire; users, bookings and reviews are durable data and belong in MySQL.

## 5. Authentication flow (built)

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthFilter
    participant S as AuthService
    participant DB as Database

    C->>S: POST /signup (name, email, password)
    S->>DB: insert passenger (BCrypt hash)
    S-->>C: 201 passenger
    C->>S: POST /login (email, password)
    S->>DB: find by email
    S-->>C: 200 signed JWT (HS256)
    C->>F: GET /me with Bearer token
    F->>F: verify signature + expiry
    F->>DB: load user by subject
    F-->>C: request continues, 200 passenger
```

Without a valid token the filter leaves the request unauthenticated and the security entry point answers 401.

## 6. Ride request flow (built)

```mermaid
sequenceDiagram
    participant P as Passenger
    participant B as booking-service
    participant L as location-service
    participant S as socket-service
    participant D as Driver app

    D->>L: PUT /drivers/{id} position every few seconds
    P->>B: POST /bookings (pickup)
    B->>B: save booking as PENDING
    B->>L: GET /nearby (5 km, nearest first, limit 1)
    L-->>B: nearest driver
    B->>S: POST /rides/notify (driver, booking)
    S-->>D: STOMP push on /topic/drivers/{id}/rides
    D->>S: STOMP /app/rides/accept
    S->>B: POST /bookings/{id}/accept
    B->>B: PENDING to CONFIRMED (version check)
    B-->>S: 200 confirmed
    S-->>D: /topic/bookings/{id} CONFIRMED
    P->>B: POST /bookings/{id}/complete
    B->>B: CONFIRMED to COMPLETED + BOOKING_COMPLETED event
```

If no driver is nearby the booking is saved as CANCELLED with reason `NO_DRIVERS_NEARBY` and the caller gets a 409. If socket-service is down, the booking stays PENDING and a warning is logged. If location-service is down, the caller gets a 503.

## 7. Review flow (built)

```mermaid
sequenceDiagram
    participant P as Passenger
    participant R as review-service
    participant B as booking-service
    participant DB as review DB

    P->>R: POST /reviews (bookingId, passengerId, rating)
    R->>B: GET /bookings/{id}
    B-->>R: booking (status, passengerId, driverId)
    R->>R: booking COMPLETED? passenger matches? not already reviewed?
    R->>DB: insert review
    R-->>P: 201 review
    P->>R: GET /reviews?driverId=9001
    R-->>P: average rating, count, reviews
```

Rules: one review per booking, only the booking's passenger can write it, rating must be 1-5, and the booking must be COMPLETED. A duplicate review, a non-completed booking or a wrong passenger is refused.

## 8. Data model

```mermaid
erDiagram
    PASSENGER ||--o{ BOOKING : requests
    DRIVER ||--o{ BOOKING : fulfils
    BOOKING ||--o| REVIEW : receives
    PASSENGER { long id string name string email string password }
    DRIVER { long id string name string email string password }
    BOOKING { long id string status long passengerId long driverId double pickupLatitude double pickupLongitude long version }
    REVIEW { long id long bookingId long passengerId long driverId int rating string comment }
```

Booking references passengers and drivers by plain id, not by a foreign key, because they live in other services' databases. Booking status values: PENDING, CONFIRMED, COMPLETED, CANCELLED. The `version` column is for optimistic locking, so two drivers cannot both accept the same ride. Review has a unique constraint on `bookingId`, so one booking can be reviewed once.

## 9. Decisions and trade-offs

| Decision | Why | Cost / what I'd change |
|----------|-----|------------------------|
| Stateless JWT instead of sessions | Any instance can verify a token, no shared session store | Tokens can't be revoked before expiry; a refresh-token flow would fix this |
| BCrypt for passwords | Slow by design, salted automatically | None for this scale |
| Shared `entity-service` library | One definition of the core entities | Compile-time coupling between services, and each consumer must scan its package (`@EntityScan`) and enable auditing itself. Review-service owns its own entity instead |
| Database per service | Services can evolve schemas independently | No cross-service joins; services call each other or react to events |
| Ids instead of foreign keys across services | A booking can't hold a real FK to a table in another database | Referential integrity is enforced in code |
| H2 and in-memory stores by default | Anyone can run the project with just a JDK | They behave differently from MySQL and Redis, so everything is re-tested against the real stores |
| Driver positions expire (TTL) | A driver whose app closed must not be offered rides | The in-memory store checks age on read; Redis uses a separate TTL key because GEO members can't expire individually |
| Plain WebSocket + STOMP with a simple in-memory broker | Easy to run and test | One socket-service instance only; scaling out needs an external broker (RabbitMQ or Redis pub/sub) |
| Socket service calls booking over REST | Simple and testable | A gRPC call would be a later, optional experiment |
| Optimistic locking for accept | Two drivers accepting at once cannot both win, with no explicit lock | The loser gets a 409 and must be told to look for another ride |
| Review checks the booking over REST | Simple, and the rule (COMPLETED + right passenger) stays in one place | A Kafka consumer would decouple the services and survive booking-service downtime; planned for the Docker stage |
| Unique constraint on review.bookingId | The database itself stops duplicate reviews, even under concurrent requests | A second insert attempt fails; the service checks first for a friendlier 409 |
| Booking events behind an interface | Same code logs locally and publishes to Kafka in Docker | At-least-once delivery with no outbox: an event can be lost if the process dies between the DB write and the publish |
| `ddl-auto=update` | Fast to develop against | Not for production; I'd switch to Flyway migrations |
| Spring Boot 3.5.16 | Supports Java 17 to 25 | Boot 3.5 no longer gets free security patches; moving to Boot 4.x is a later upgrade |
| Maven multi-module build | One parent POM pins versions for every service | A change to the library rebuilds its dependants |

## 10. Known gaps

- No refresh tokens or email verification, and only passengers can log in so far (driver login is not built).
- Location, socket, booking and review have no authentication yet; the gateway in Phase 7 is where JWT checking moves, and these services should then not be reachable directly.
- Booking only tries the single nearest driver. There is no timeout, no re-dispatch to the next driver if they ignore the ride, and the chosen driver (`candidateDriverId`) is returned on create but not stored, so `accept` does not check that the accepting driver is the one who was notified.
- Booking driver ids must be numeric, while location accepts any string id.
- Creating a booking is not idempotent: a retried request creates a second booking (an idempotency key is planned for Phase 7).
- Review-service checks the booking synchronously over REST. If booking-service is down, reviews cannot be created (503). The Kafka-based flow is planned for the Docker stage.
- No integration tests against real Redis, MySQL or Kafka (Testcontainers is planned for the Docker stage); the Kafka publisher and consumer have not been run yet.
- The WebSocket flow is tested by hand in the browser page, not by an automated test.
- The auth service creates tables for every entity in the shared library, not just `passenger`.
- Default JWT secret in `application.properties` is for local use only. Set `JWT_SECRET` anywhere else.
- On Windows PowerShell 5.1 the `health status is UP` line in some smoke tests reports a false failure (the Actuator content type comes back as bytes).

## 11. Roadmap

7. **Resilience and edge**: gateway routing and JWT check, circuit breaker on booking to socket calls, tracing, idempotency key on booking creation, driver re-dispatch on timeout.
8. **React frontend**: booking form, live driver map (Leaflet + OpenStreetMap, free).
9. **Docker**: Compose for every service with MySQL, Redis and Kafka, a Kafka consumer in review-service, and a full Docker test guide.
