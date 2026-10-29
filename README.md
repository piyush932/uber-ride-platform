# Uber Ride Platform

A ride-hailing backend I'm building phase by phase to practise real microservice problems: service discovery, stateless auth, geospatial search, real-time push and event-driven flows. Every phase is small enough to run and test on its own.

Stack: Java (builds to Java 17 bytecode, runs on 17 to 25), Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven.

The plan is: **run and test everything locally first** (no Docker needed), then switch to Docker at the end. Every service runs with in-memory defaults, and also supports real MySQL / Redis / Kafka through Spring profiles.

## Where the project is right now

| Phase | What | Status |
|-------|------|--------|
| 1 | Project skeleton, Maven multi-module build, helper scripts | Done |
| 2 | Eureka registry, shared entity library, Auth service (signup, login, JWT) | Done |
| 3 | Location service (driver positions, nearby search) | Done |
| 4 | Socket service (WebSocket/STOMP push to drivers) | Done |
| 5 | Booking service (orchestrates location + socket, race-safe accept, events) | Done (needs your local run to confirm) |
| 6 | Review service | Next |
| 7 | API gateway, circuit breakers, tracing | Planned |
| 8 | React frontend (booking form + live map) | Planned |
| Final | Docker Compose with MySQL, Redis, Kafka | Planned |

[docs/HLD.md](docs/HLD.md) describes the full target design and marks what is built.

## What the code solves so far

- **Who is calling?** Passengers sign up and log in, and receive a signed JWT.
- **Where do services live?** Services register with Eureka instead of using hard-coded host names.
- **Where are the drivers?** Drivers report positions; the location service answers "who is within 5 km, nearest first". Drivers who stop reporting drop out automatically.
- **How does a driver hear about a ride instantly?** The socket service pushes ride requests over WebSocket instead of the app polling, and forwards the driver's accept.
- **How does a ride get booked?** The booking service saves the booking, asks location for the nearest driver, asks socket to notify them, and confirms the booking when the driver accepts. A second accept is refused (optimistic locking on the `version` column).
- **One domain model, many services.** `entity-service` holds the JPA entities.

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
  backend/                    Maven multi-module build (parent pom.xml)
    entity-service/           shared JPA entities (library)
    eureka-server/            service registry          :8761
    auth-service/             signup / login / JWT      :8081
    location-service/         driver positions          :8082
    socket-service/           WebSocket push            :8083
    booking-service/          bookings                  :8084
  docker-compose.yml          final step, only covers MySQL, Eureka and auth for now
  docs/HLD.md                 architecture and design decisions
  postman/                    importable test collections (Phase 1-2, 3-4 and 5)
```

## What you need on your laptop

- A JDK, version 17 or newer (JDK 25 works). `java -version` must print a version, and `JAVA_HOME` should point at the JDK folder.
- Nothing else for the in-memory mode. Maven is downloaded for you by `scripts/mvn.ps1`.
- Optional, for the real-store mode: MySQL 8 (auth and booking) and Redis (location). Kafka is not needed until the Docker stage.
- A browser with internet access for the socket test page (it loads stomp.js from a CDN).
- Optional: Postman, IntelliJ IDEA.

## Build and start (Windows PowerShell)

Open PowerShell in the repo root (the folder that contains `backend` and `scripts`).

**Step 1: allow the helper scripts for this window only**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

**Step 2: build everything** (the first run downloads Maven and dependencies, so allow a few minutes)

```powershell
.\scripts\mvn.ps1 clean install -DskipTests
```

The output must end with `BUILD SUCCESS`. Then check that every service has its jar:

```powershell
dir backend\*\target\*.jar
```

You should see five runnable jars (eureka-server, auth-service, location-service, socket-service, booking-service) plus entity-service. If `booking-service.jar` is missing, the build failed: scroll up to the first `[ERROR]` line.

**Step 3a: start with in-memory defaults**

```powershell
.\scripts\start-all.ps1
```

This opens five windows. Wait about 30 seconds. Open http://localhost:8761 and you should see `AUTH-SERVICE`, `LOCATION-SERVICE`, `SOCKET-SERVICE` and `BOOKING-SERVICE` listed.

**Step 3b: start with your local MySQL and Redis**

Open one PowerShell window per service in the repo root. Each setting only lasts for that window.

| Window | Commands |
|---|---|
| Eureka | `java -jar backend\eureka-server\target\eureka-server.jar` |
| Auth on MySQL | `$env:SPRING_PROFILES_ACTIVE='mysql'` then `$env:DB_PASSWORD='your-mysql-password'` then `java -jar backend\auth-service\target\auth-service.jar` |
| Location on Redis | `$env:SPRING_PROFILES_ACTIVE='redis'` then `java -jar backend\location-service\target\location-service.jar` |
| Socket | `java -jar backend\socket-service\target\socket-service.jar` |
| Booking on MySQL | `$env:SPRING_PROFILES_ACTIVE='mysql'` then `$env:DB_PASSWORD='your-mysql-password'` then `java -jar backend\booking-service\target\booking-service.jar` |

The MySQL profile creates the databases `uber_auth` and `uber_booking` itself (the user needs permission to create databases; root has it). Other settings: `DB_USER`, `DB_PORT`, `REDIS_HOST`, `REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`.

Eureka is optional: set `$env:EUREKA_ENABLED='false'` in a window before starting a service there and it runs on its own.

## Test Phase 2: auth

```powershell
.\scripts\smoke-test-auth.ps1
```

It covers signup (201), duplicate (409), invalid input (400), login, wrong password (401), unknown email (401), and `/me` with a good token, no token and a garbage token.

Known cosmetic issue: on Windows PowerShell 5.1 the line `health status is UP` can report a failure even though the service is UP, because the Actuator content type is returned as bytes. Everything else passing means the service is fine.

Token expiry: stop auth, run `$env:JWT_EXPIRY_MS='5000'`, start it again, log in and call `/api/v1/auth/me` at once (200), wait 6 seconds, call again (401).

With MySQL: `mysql -u root -p -e "select id, email, left(password, 7) as pw from uber_auth.passenger;"` shows the stored hash starting with `$2a$10$`, and the data survives an auth restart.

## Test Phase 3: location

```powershell
.\scripts\smoke-test-location.ps1      # expect: 14 checks, 0 failed
```

By hand:

```powershell
$loc = 'http://localhost:8082/api/v1/locations'
Invoke-RestMethod -Method Put -Uri "$loc/drivers/d1" -ContentType 'application/json' -Body '{"latitude":12.9720,"longitude":77.5950}'
Invoke-RestMethod -Uri "$loc/nearby?lat=12.9716&lng=77.5946&radiusKm=5"
```

The search returns `d1` with `distanceKm` around 0.062. With the `redis` profile you can also check Redis directly: `redis-cli zrange drivers:geo 0 -1` lists `d1`, and `redis-cli ttl driver:seen:d1` counts down from 300.

Drivers who go quiet disappear: start location with `$env:LOCATION_TTL_SECONDS='10'`, save a driver, search (found), wait 11 seconds, search again (empty).

## Test Phase 4: socket

```powershell
.\scripts\smoke-test-socket.ps1
```

Live WebSocket test in the browser:

1. Open http://localhost:8083/test.html, leave driver id `7`, click **Connect**.
2. Click **Send test ride request**. The log shows `RIDE REQUEST: {...}`.
3. Open a second tab as driver `8`. A ride sent to driver 7 does not appear there.
4. Click **Accept ride**. With booking-service stopped this shows `ACCEPT_FAILED`; with it running see the Phase 5 test below.

## Test Phase 5: booking

**A. Automated**

```powershell
.\scripts\smoke-test-booking.ps1      # expect: 13 checks, 0 failed
```

It puts driver 9001 online, creates a booking (201, PENDING, nearest driver 9001), accepts it (200, CONFIRMED), tries a second accept (409), cancels it, then checks that a booking with no driver nearby gives 409 and a booking without `passengerId` gives 400.

**B. The full ride flow, end to end**

1. Put driver 9001 online:
   ```powershell
   Invoke-RestMethod -Method Put -Uri 'http://localhost:8082/api/v1/locations/drivers/9001' -ContentType 'application/json' -Body '{"latitude":12.9720,"longitude":77.5950}'
   ```
2. Open http://localhost:8083/test.html, set the driver id to `9001`, click **Connect**.
3. Create a booking as a passenger:
   ```powershell
   Invoke-RestMethod -Method Post -Uri 'http://localhost:8084/api/v1/bookings' -ContentType 'application/json' -Body '{"passengerId":1,"pickupLatitude":12.9716,"pickupLongitude":77.5946}'
   ```
   The response shows `status: PENDING` and `candidateDriverId: 9001`. The browser log shows a `RIDE REQUEST`, and the booking window prints a `DRIVER_NOTIFIED` event.
4. In the browser click **Accept ride**. The log shows `BOOKING UPDATE ... CONFIRMED`.
5. `Invoke-RestMethod http://localhost:8084/api/v1/bookings/1` shows `CONFIRMED` with `driverId 9001`. With MySQL: `mysql -u root -p -e "select id, status, passenger_id, driver_id, version from uber_booking.booking;"`.
6. Click **Accept ride** again. The booking window logs a 409 and the page shows `ACCEPT_FAILED`.

**C. Failure cases**

- Stop socket-service and create a booking: still 201 PENDING, with a warning in the booking log.
- Stop location-service and create a booking: 503.
- Make a non-numeric driver id (such as `d1`) the only nearby driver: 409 (booking needs numeric driver ids).

**D. Unit tests (all modules)**

```powershell
.\scripts\mvn.ps1 test
```

Expected: `Tests run: 47` in total (16 auth, 14 location, 7 socket, 10 booking) and `BUILD SUCCESS`.

**E. Postman**

Import `postman/Uber-Ride-Platform-Stage3.postman_collection.json` and run it top to bottom.

## Using IntelliJ instead

Open the repo folder, right-click `backend/pom.xml` and choose Add as Maven Project. Set the project SDK to your JDK (17 or newer) and enable the Lombok plugin and annotation processing. Run each `*Application` class with the green arrow beside its `main` method.

## Troubleshooting

| What you see | Likely cause and fix |
|---|---|
| `running scripts is disabled on this system` | Run the `Set-ExecutionPolicy -Scope Process ...` line from Step 1 in that window |
| `Unable to access jarfile ...booking-service.jar` | The build did not finish. Run `git pull`, then `.\scripts\mvn.ps1 clean install -DskipTests` and look for the first `[ERROR]` line |
| `Script can't download Maven` | Check your internet or proxy, or install Maven yourself; the script uses it when found |
| `release version 17 not supported` | Your `java` is older than 17. Install JDK 17 or newer and set `JAVA_HOME` |
| `Port 8081 / 8082 / 8083 / 8084 / 8761 was already in use` | An old instance is still running. Close its window, or start with `--server.port=9084` |
| Repeating `Connection refused ... 8761` in a service log | Eureka isn't running. Start it or set `$env:EUREKA_ENABLED='false'` |
| `Could not find artifact com.example:entity-service` | Run Step 2 (`clean install`) first |
| `Access denied for user 'root'` | `DB_PASSWORD` is missing or wrong in that window |
| Location fails to start with the redis profile | Redis is not running: `redis-cli ping` must answer `PONG` |
| Booking returns 503 | location-service is not reachable on port 8082 |
| Booking returns 409 `No drivers nearby` | Put a driver online first (PUT to location) within 5 km of the pickup |
| Test page says `WebSocket error` | socket-service isn't running, or the page was opened from another port |
| Test page shows no `RIDE REQUEST` | The driver id in the page must match the driver the booking picked |
| IDE shows red `getEmail()` or `builder()` | Enable Lombok annotation processing in the IDE; Maven itself builds fine |
| A test fails | Copy the test name and the first error lines |

## Docker (final step, not needed yet)

`docker-compose.yml` currently covers MySQL, Eureka and auth only. The final stage extends it to every service, switches them to MySQL, Redis (`SPRING_PROFILES_ACTIVE=redis` for location) and Kafka (`SPRING_PROFILES_ACTIVE=kafka` for booking), and adds a Docker test guide.

## Design notes

See [docs/HLD.md](docs/HLD.md) for diagrams, the data model, the flows and the trade-offs I made on purpose.
