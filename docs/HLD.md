# High-Level Design

This document covers the whole target system, and says clearly which parts exist today.

## 1. Goal

Simulate the core flow of a ride-hailing app: a passenger requests a ride, nearby drivers are found, one driver is notified instantly, accepts, and the ride can later be reviewed. The point is to practise the distributed-systems problems behind that flow, not to clone a product.

## 2. What is built (Phase 1-4)

```mermaid
flowchart LR
    C[Client: Postman / PowerShell / browser] -->|REST + JWT| A[auth-service :8081]
    C -->|REST| L[location-service :8082]
    C -->|REST + WebSocket| S[socket-service :8083]
    A --> E[eureka-server :8761]
    L --> E
    S --> E
    A -->|JPA| M[(H2, or MySQL)]
    L --> ST[(in-memory store, or Redis GEO)]
    A -. uses .-> LIB[entity-service library]
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
    LOC --> R[(Redis GEO)]
    AUTH --> DB1[(DB)]
    BOOK --> DB2[(DB)]
    REV --> DB3[(DB)]
    AUTH & BOOK & LOC & SOCK & REV & GW --> EU[Eureka]
```

Phases 5-8 add booking, review, the gateway and the frontend. Each service has its own database or store.

## 4. Local mode and Docker mode

Every service has two ways to run, chosen by a Spring profile, so the same code is tested locally first and in Docker last:

| Concern | Local (default) | Docker (final step) |
|---------|-----------------|---------------------|
| Database | H2 in memory | MySQL, profile `mysql` |
| Driver locations (Phase 3, built) | In-memory store | Redis GEO, profile `redis` |
| Booking events (Phase 5) | Logged by an in-process publisher | Kafka, profile `kafka` |
| Service discovery | Eureka on localhost | Eureka container |

The in-memory and real implementations sit behind the same interface (`LocationStore` today), which is also a useful design point to explain in an interview.

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

## 6. Ride request flow (target; location and socket parts are built)

```mermaid
sequenceDiagram
    participant P as Passenger
    participant B as booking-service (Phase 5)
    participant L as location-service
    participant S as socket-service
    participant D as Driver app

    D->>L: PUT /drivers/{id} position every few seconds
    P->>B: POST /bookings (pickup)
    B->>L: GET /nearby (5 km, nearest first)
    L-->>B: drivers
    B->>S: POST /rides/notify (driver, booking)
    S-->>D: STOMP push on /topic/drivers/{id}/rides
    D->>S: STOMP /app/rides/accept
    S->>B: POST /bookings/{id}/accept
    B-->>S: confirmed
    S-->>D: /topic/bookings/{id} CONFIRMED
```

## 7. Data model

```mermaid
erDiagram
    PASSENGER ||--o{ BOOKING : requests
    DRIVER ||--o{ BOOKING : fulfils
    PASSENGER { long id string name string email string password }
    DRIVER { long id string name string email string password }
    BOOKING { long id string status long passengerId long driverId double pickupLatitude double pickupLongitude long version }
```

All entities extend `BaseModel` (id, createdAt, updatedAt). Booking references passengers and drivers by plain id, not by a foreign key, because they live in other services' databases. Status values: PENDING, CONFIRMED, COMPLETED, CANCELLED. The `version` column is for optimistic locking, so two drivers cannot both accept the same ride.

## 8. Decisions and trade-offs

| Decision | Why | Cost / what I'd change |
|----------|-----|------------------------|
| Stateless JWT instead of sessions | Any instance can verify a token, no shared session store | Tokens can't be revoked before expiry; a refresh-token flow would fix this |
| BCrypt for passwords | Slow by design, salted automatically | None for this scale |
| Shared `entity-service` library | One definition of the core entities | Compile-time coupling between services. A stricter setup gives each service its own tables and API contracts |
| Database per service | Services can evolve schemas independently | No cross-service joins; services call each other or react to events |
| Ids instead of foreign keys across services | A booking can't hold a real FK to a table in another database | Referential integrity is enforced in code |
| H2 and in-memory stores by default | Anyone can run the project with just a JDK | They behave differently from MySQL and Redis, so the Docker stage re-tests everything |
| Driver positions expire (TTL) | A driver whose app closed must not be offered rides | The in-memory store checks age on read; Redis uses a separate TTL key because GEO members can't expire individually |
| Plain WebSocket + STOMP with a simple in-memory broker | Easy to run and test | One socket-service instance only; scaling out needs an external broker (RabbitMQ or Redis pub/sub) |
| Socket service calls booking over REST | Simple and testable | A gRPC call would be a later, optional experiment |
| `ddl-auto=update` | Fast to develop against | Not for production; I'd switch to Flyway migrations |
| Spring Boot 3.5.16 | Supports Java 17 to 25 | Boot 3.5 no longer gets free security patches; moving to Boot 4.x is a later upgrade |
| Maven multi-module build | One parent POM pins versions for every service | A change to the library rebuilds its dependants |

## 9. Known gaps

- No refresh tokens or email verification, and only passengers can log in so far (driver login is not built).
- The location and socket services have no authentication yet; the gateway in Phase 7 is where JWT checking moves, and these services should then not be reachable directly.
- No integration tests against real Redis, MySQL or Kafka (Testcontainers is planned for the Docker stage).
- The WebSocket flow is tested by hand in the browser page, not by an automated test.
- The auth service creates tables for every entity in the shared library, not just `passenger`.
- Default JWT secret in `application.properties` is for local use only. Set `JWT_SECRET` anywhere else.

## 10. Roadmap

5. **Booking service**: create a booking, call location and socket, confirm on driver accept (race-safe), publish events.
6. **Review service**: ratings tied to a completed booking.
7. **Resilience and edge**: gateway routing and JWT check, circuit breaker on booking to socket calls, tracing, idempotency key on booking creation.
8. **React frontend**: booking form, live driver map (Leaflet + OpenStreetMap, free).
9. **Docker**: Compose for every service with MySQL, Redis and Kafka, and a full Docker test guide.
