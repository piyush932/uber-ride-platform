# Uber Ride Platform

A ride-hailing backend I'm building phase by phase to practise real microservice problems: service discovery, stateless auth, geospatial search, real-time push, event-driven flows, cross-service rules and edge resilience. Every phase is small enough to run and test on its own.

Stack: Java (builds to Java 17 bytecode, runs on 17 to 25), Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven. Frontend: React 18 + Vite + Leaflet.

The plan is: **run and test everything locally first** (no Docker needed), then switch to Docker at the end. Every service runs with in-memory defaults, and also supports real MySQL / Redis / Kafka through Spring profiles.

## Where the project is right now

| Phase | What | Status |
|-------|------|--------|
| 1 | Project skeleton, Maven multi-module build, helper scripts | Done |
| 2 | Eureka registry, shared entity library, Auth service (signup, login, JWT) | Done |
| 3 | Location service (driver positions, nearby search) | Done |
| 4 | Socket service (WebSocket/STOMP push to drivers) | Done |
| 5 | Booking service (orchestrates location + socket, race-safe accept, events) | Done |
| 6 | Review service (ratings for completed bookings) | Done |
| 7 | API gateway (JWT check, circuit breakers, optional rate limiting), booking idempotency | Done |
| 8 | React passenger frontend (login, Leaflet map booking, live status, my rides) | Done (needs your local run to confirm) |
| Final | Docker Compose with MySQL, Redis, Kafka; driver panel and WebSocket push to the UI | Planned |

[docs/HLD.md](docs/HLD.md) describes the full target design and marks what is built.

## What the code solves so far

- **Who is calling?** Passengers sign up and log in, and receive a signed JWT.
- **Where do services live?** Services register with Eureka; the gateway routes to them with `lb://` instead of hard-coded hosts.
- **Who is allowed in?** The gateway checks the JWT on every route except signup, login, health and fallbacks, and forwards the user's email downstream in `X-User-Email`.
- **Where are the drivers?** Drivers report positions; the location service answers "who is within 5 km, nearest first". Drivers who stop reporting drop out automatically.
- **How does a driver hear about a ride instantly?** The socket service pushes ride requests over WebSocket; booking-service calls it through a circuit breaker.
- **How does a ride get booked?** The booking service saves the booking, asks location for the nearest driver, asks socket to notify them, and confirms the booking when the driver accepts. A second accept is refused (optimistic locking).
- **What if the passenger retries?** With an `Idempotency-Key` header, the same key returns the same booking instead of creating a second one.
- **How does a ride end?** A confirmed booking can be completed, which is the trigger for a review.
- **How is a driver rated?** The review service accepts one review per completed booking, from the passenger who made it, and computes a driver's average rating.
- **What does the passenger see?** A responsive web app: log in, pick pickup and dropoff on a map, book, watch the ride status, and see past rides.

## Repo layout

```
uber-ride-platform/
  scripts/
    mvn.ps1                   runs Maven (downloads it into .tools/ if you don't have it)
    start-all.ps1             opens one window per service (in-memory defaults)
    smoke-test-auth.ps1       automated checks for auth
    smoke-test-location.ps1   14 automated checks for location
    smoke-test-socket.ps1     automated checks for the socket REST side
    smoke-test-booking.ps1    13 automated checks for booking
    smoke-test-review.ps1     13 automated checks for review
    smoke-test-gateway.ps1    automated checks for the gateway
  frontend/                   React passenger app (Vite + Leaflet)
  backend/                    Maven multi-module build (parent pom.xml)
    entity-service/           shared JPA entities (library)
    eureka-server/            service registry          :8761
    auth-service/             signup / login / JWT      :8081
    location-service/         driver positions          :8082
    socket-service/           WebSocket push            :8083
    booking-service/          bookings                  :8084
    review-service/           ratings                   :8085
    api-gateway/              single entry point        :8080
  docker-compose.yml          final step, only covers MySQL, Eureka and auth for now
  docs/HLD.md                 architecture and design decisions
  postman/                    importable test collections (Phase 1-2, 3-4, 5 and 6)
```

## What you need on your laptop

- A JDK, version 17 or newer (JDK 25 works). `java -version` must print a version, and `JAVA_HOME` should point at the JDK folder.
- Node.js 18 or newer for the frontend (`node -v` must print a version).
- Nothing else for the in-memory mode. Maven is downloaded for you by `scripts/mvn.ps1`.
- Optional: MySQL 8 (auth, booking, review) and Redis (location; also gateway rate limiting). Kafka is not needed until the Docker stage.
- A browser with internet access for the map tiles and the socket test page.
- Optional: Postman, IntelliJ IDEA, VS Code.

## Build and start the backend (Windows PowerShell)

Open PowerShell in the repo root (the folder that contains `backend`, `frontend` and `scripts`).

**Step 1: allow the helper scripts for this window only**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

**Step 2: build everything**

```powershell
git pull
.\scripts\mvn.ps1 clean install -DskipTests
```

The output must end with `BUILD SUCCESS`. Then check that every service has its jar:

```powershell
dir backend\*\target\*.jar
```

You should see seven runnable jars (eureka-server, auth-service, location-service, socket-service, booking-service, review-service, api-gateway) plus entity-service. If a jar is missing, the build failed: scroll up to the first `[ERROR]` line.

**Step 3a: start with in-memory defaults**

```powershell
.\scripts\start-all.ps1
```

This opens seven windows. Wait about 30 seconds. Open http://localhost:8761 and you should see `AUTH-SERVICE`, `LOCATION-SERVICE`, `SOCKET-SERVICE`, `BOOKING-SERVICE`, `REVIEW-SERVICE` and `API-GATEWAY` listed. The gateway is http://localhost:8080.

**Step 3b: start with your local MySQL and Redis**

Open one PowerShell window per service in the repo root. Each setting only lasts for that window.

| Window | Commands |
|---|---|
| Eureka | `java -jar backend\eureka-server\target\eureka-server.jar` |
| Auth on MySQL | `$env:SPRING_PROFILES_ACTIVE='mysql'` then `$env:DB_PASSWORD='your-mysql-password'` then `java -jar backend\auth-service\target\auth-service.jar` |
| Location on Redis | `$env:SPRING_PROFILES_ACTIVE='redis'` then `java -jar backend\location-service\target\location-service.jar` |
| Socket | `java -jar backend\socket-service\target\socket-service.jar` |
| Booking on MySQL | `$env:SPRING_PROFILES_ACTIVE='mysql'` then `$env:DB_PASSWORD='your-mysql-password'` then `java -jar backend\booking-service\target\booking-service.jar` |
| Review on MySQL | `$env:SPRING_PROFILES_ACTIVE='mysql'` then `$env:DB_PASSWORD='your-mysql-password'` then `java -jar backend\review-service\target\review-service.jar` |
| Gateway | `java -jar backend\api-gateway\target\api-gateway.jar` |

Optional gateway rate limiting with your local Redis (Redis must be running, `redis-cli ping` answers `PONG`):

```powershell
$env:SPRING_PROFILES_ACTIVE='redis'
java -jar backend\api-gateway\target\api-gateway.jar
```

The MySQL profile creates the databases `uber_auth`, `uber_booking` and `uber_review` itself. Other settings: `DB_USER`, `DB_PORT`, `REDIS_HOST`, `REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`, `JWT_SECRET`.

Eureka is optional: set `$env:EUREKA_ENABLED='false'` in a window before starting a service there and it runs on its own.

## Run the frontend (Phase 8)

In a new PowerShell window, from the repo root:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The frontend calls the gateway at http://localhost:8080 (change it with `VITE_API_URL` in a `.env` file if needed).

**Test the passenger flow**

1. Sign up with an email and password (or log in if you already have an account).
2. You land on the booking screen with a map.
3. Tap the map once for pickup (green), once more for dropoff (red).
4. Press **Book ride**. You are taken to the ride status screen.
5. The status screen refreshes every 3 seconds. With a driver online near the pickup, the booking should reach `CONFIRMED` after the driver accepts in the socket test page.
6. Use **Cancel ride** (pending/confirmed) or **Complete ride** (confirmed) to change the state.
7. Open **My rides** to see all your bookings and reopen any of them.

Known limitation: the passenger UI polls the booking every 3 seconds; live WebSocket push to the passenger screen is planned with the driver panel.

## Test Phase 2: auth

```powershell
.\scripts\smoke-test-auth.ps1
```

Known cosmetic issue: on Windows PowerShell 5.1 the line `health status is UP` can report a failure even though the service is UP, because the Actuator content type is returned as bytes.

## Test Phase 3: location

```powershell
.\scripts\smoke-test-location.ps1      # expect: 14 checks, 0 failed
```

## Test Phase 4: socket

```powershell
.\scripts\smoke-test-socket.ps1
```

## Test Phase 5: booking

```powershell
.\scripts\smoke-test-booking.ps1      # expect: 13 checks, 0 failed
```

## Test Phase 6: review

```powershell
.\scripts\smoke-test-review.ps1       # expect: 13 checks, 0 failed
```

## Test Phase 7: gateway

```powershell
.\scripts\smoke-test-gateway.ps1
```

It signs up and logs in through the gateway, checks that bookings without a token give 401 and with a garbage token give 401, calls `/me` with a valid token, puts a driver online, creates a booking with an `Idempotency-Key`, repeats with the same key and checks the same booking comes back, and reads reviews through the gateway.

## Test Phase 8: frontend

With the backend running (gateway on 8080):

```powershell
cd frontend
npm install
npm run dev
```

Then in the browser: sign up, tap two map points, press Book ride, watch the status screen, and check My rides. If booking creation fails, check the browser console and the booking-service window; a 409 means no driver was online near the pickup.

## Using IntelliJ instead

Open the repo folder, right-click `backend/pom.xml` and choose Add as Maven Project. Set the project SDK to your JDK (17 or newer) and enable the Lombok plugin and annotation processing. Run each `*Application` class with the green arrow beside its `main` method.

## Troubleshooting

| What you see | Likely cause and fix |
|---|---|
| `running scripts is disabled on this system` | Run the `Set-ExecutionPolicy -Scope Process ...` line from Step 1 in that window |
| `Unable to access jarfile ...jar` | The build did not finish. Run `git pull`, then `.\scripts\mvn.ps1 clean install -DskipTests` and look for the first `[ERROR]` line |
| Frontend shows a network/CORS error | The gateway must be running on 8080; it allows only `http://localhost:5173` |
| Booking fails with 409 in the frontend | No driver was online near the pickup point; put a driver online first |
| Gateway returns 401 on a route you expected to work | Send `Authorization: Bearer <token>`; signup and login are the only auth routes that do not need one |
| Gateway returns 503 with a JSON error | That service is down or its circuit breaker opened; start the service and wait ~10 seconds |
| Gateway rate limiting returns 429 | Expected with the redis profile after more than 10 quick requests from one IP |
| Gateway fails to start with the redis profile | Redis is not running: `redis-cli ping` must answer `PONG` |
| `Port 8080 was already in use` | An old gateway instance is still running |
| Repeating `Connection refused ... 8761` | Eureka isn't running |
| Booking returns 503 | location-service is not reachable on port 8082 |
| Review returns 409 `must be COMPLETED` | Complete the booking first |
| A test fails | Copy the test name and the first error lines |

## Docker (final step, not needed yet)

`docker-compose.yml` currently covers MySQL, Eureka and auth only. The final stage extends it to every service, switches them to MySQL, Redis (`SPRING_PROFILES_ACTIVE=redis` for location and gateway) and Kafka (`SPRING_PROFILES_ACTIVE=kafka` for booking), and adds a Docker test guide.

## Design notes

See [docs/HLD.md](docs/HLD.md) for diagrams, the data model, the flows and the trade-offs I made on purpose.
