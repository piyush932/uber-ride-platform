# Uber Ride Platform

A ride-hailing backend I'm building phase by phase to practise real microservice problems: service discovery, stateless auth, geospatial search, real-time push and event-driven flows. Every phase is small enough to run and test on its own.

Stack: Java (builds to Java 17 bytecode, runs on 17 to 25), Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven.

The plan is: **run and test everything locally first** (no Docker, no database server), then switch to Docker at the end.

## Where the project is right now

| Phase | What | Status |
|-------|------|--------|
| 1 | Project skeleton, Maven multi-module build, helper scripts | Done |
| 2 | Eureka registry, shared entity library, Auth service (signup, login, JWT) | Done |
| 3 | Location service (driver positions, nearby search) | Done |
| 4 | Socket service (WebSocket/STOMP push to drivers) | Done |
| 5 | Booking service (orchestrates location + socket, events) | Next |
| 6 | Review service | Planned |
| 7 | API gateway, circuit breakers, tracing | Planned |
| 8 | React frontend (booking form + live map) | Planned |
| Final | Docker Compose with MySQL, Redis, Kafka | Planned |

[docs/HLD.md](docs/HLD.md) describes the full target design and marks what is built.

## What the code solves so far

- **Who is calling?** Passengers sign up and log in, and receive a signed JWT.
- **Where do services live?** Services register with Eureka instead of using hard-coded host names.
- **Where are the drivers?** Drivers report positions; the location service answers "who is within 5 km, nearest first". Drivers who stop reporting drop out automatically.
- **How does a driver hear about a ride instantly?** The socket service pushes ride requests over WebSocket instead of the app polling, and receives the driver's accept.
- **One domain model, many services.** `entity-service` holds the JPA entities.

## Repo layout

```
uber-ride-platform/
  scripts/
    mvn.ps1                  runs Maven (downloads it into .tools/ if you don't have it)
    start-all.ps1            opens one window per service
    smoke-test-auth.ps1      14 automated checks for auth
    smoke-test-location.ps1  14 automated checks for location
    smoke-test-socket.ps1    4 automated checks for the socket REST side
  backend/                   Maven multi-module build (parent pom.xml)
    entity-service/          shared JPA entities (library)
    eureka-server/           service registry          :8761
    auth-service/            signup / login / JWT      :8081
    location-service/        driver positions          :8082
    socket-service/          WebSocket push            :8083
  docker-compose.yml         final step, only covers MySQL, Eureka and auth for now
  docs/HLD.md                architecture and design decisions
  postman/                   importable test collections (Phase 1-2 and Phase 3-4)
```

## What you need on your laptop

- A JDK, version 17 or newer (JDK 25 works). `java -version` must print a version, and `JAVA_HOME` should point at the JDK folder.
- Nothing else. Maven is downloaded for you by `scripts/mvn.ps1`; the database and driver store are in memory.
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

Expected: `BUILD SUCCESS`, and a `target\<service>.jar` in each service folder.

**Step 3: start all services**

```powershell
.\scripts\start-all.ps1
```

This opens four windows (Eureka, auth, location, socket). Wait about 30 seconds. Open http://localhost:8761 and you should see `AUTH-SERVICE`, `LOCATION-SERVICE` and `SOCKET-SERVICE` listed.

Prefer to start them by hand? In separate windows:

```powershell
java -jar backend\eureka-server\target\eureka-server.jar
java -jar backend\auth-service\target\auth-service.jar
java -jar backend\location-service\target\location-service.jar
java -jar backend\socket-service\target\socket-service.jar
```

Eureka is optional: set `$env:EUREKA_ENABLED='false'` in a window before starting a service there and it runs on its own.

## Test Phase 2: auth

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\smoke-test-auth.ps1
```

Expected: 14 green `PASS` lines and `14 checks, 0 failed`. It covers signup (201), duplicate (409), invalid input (400), login, wrong password (401), unknown email (401), and `/me` with a good token, no token and a garbage token.

Token expiry: stop auth, run `$env:JWT_EXPIRY_MS='5000'`, start it again, log in and call `/api/v1/auth/me` at once (200), wait 6 seconds, call again (401).

Postman: import `postman/Uber-Ride-Platform.postman_collection.json` and run it; all 10 requests should be green.

## Test Phase 3: location

**A. Automated**

```powershell
.\scripts\smoke-test-location.ps1
```

Expected: `14 checks, 0 failed`. It saves three drivers (about 0.06 km, 1.1 km and 28 km from the search point) and checks that a 5 km search returns the first two nearest first, a 50 km search also returns the far one, bad coordinates give 400, and a driver taken offline disappears.

**B. By hand**

```powershell
$loc = 'http://localhost:8082/api/v1/locations'
Invoke-RestMethod -Method Put -Uri "$loc/drivers/d1" -ContentType 'application/json' -Body '{"latitude":12.9720,"longitude":77.5950}'
Invoke-RestMethod -Uri "$loc/nearby?lat=12.9716&lng=77.5946&radiusKm=5"
```

The PUT prints nothing (204). The search prints a list containing `d1` with `distanceKm` around 0.062.

**C. Drivers who go quiet disappear**

Stop location, then start it with `$env:LOCATION_TTL_SECONDS='10'`. Save a driver, search (found), wait 11 seconds, search again (empty list). The default is 300 seconds.

**D. Unit tests**

```powershell
.\scripts\mvn.ps1 -pl location-service -am test
```

Expected: `Tests run: 14, Failures: 0, Errors: 0` and `BUILD SUCCESS` (3 distance, 6 store, 5 controller).

**E. Postman**

Import `postman/Uber-Ride-Platform-Stage2.postman_collection.json`. Requests 1 to 7 cover location; all should be green.

## Test Phase 4: socket

**A. REST side, automated**

```powershell
.\scripts\smoke-test-socket.ps1
```

Expected: `4 checks, 0 failed`.

**B. Live WebSocket, in the browser (the real test)**

1. Open http://localhost:8083/test.html in a browser tab. Leave driver id `7` and click **Connect**. The log shows `connected as driver 7`.
2. Click **Send test ride request**. The log shows `notify REST call returned HTTP 202` and right after it `RIDE REQUEST: {...}` with the booking id and pickup coordinates. That push arrived with no polling.
3. Open a second tab at the same address, change the driver id to `8`, and Connect. In tab 1 send another ride request. Only tab 1 (driver 7) shows it; tab 2 stays quiet. That proves each driver only gets their own rides.
4. In tab 1 click **Accept ride**. The log shows `BOOKING UPDATE` with status `ACCEPT_FAILED`. This is the expected result for now: the socket service tried to confirm the ride with the booking service, which does not exist until Phase 5. After Phase 5 the same click returns `CONFIRMED`.

**C. Unit tests**

```powershell
.\scripts\mvn.ps1 -pl socket-service -am test
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`.

**D. Everything at once**

```powershell
.\scripts\mvn.ps1 test
```

Expected: `Tests run: 37` in total (16 auth, 14 location, 7 socket) and `BUILD SUCCESS`.

**E. Postman**

Requests 8 to 10 in the Stage 2 collection cover the socket REST endpoint.

## Using IntelliJ instead

Open the repo folder, right-click `backend/pom.xml` and choose Add as Maven Project. Set the project SDK to your JDK (17 or newer) and enable the Lombok plugin and annotation processing. Run each `*Application` class with the green arrow beside its `main` method.

## Troubleshooting

| What you see | Likely cause and fix |
|---|---|
| `running scripts is disabled on this system` | Run the `Set-ExecutionPolicy -Scope Process ...` line from Step 1 in that window |
| Script can't download Maven | Check your internet or proxy, or install Maven yourself; the script uses it when found |
| `release version 17 not supported` | Your `java` is older than 17. Install JDK 17 or newer and set `JAVA_HOME` |
| `Port 8081 / 8082 / 8083 / 8761 was already in use` | An old instance is still running. Close its window, or start with `--server.port=9082` |
| Repeating `Connection refused ... 8761` in a service log | Eureka isn't running. Start it or set `$env:EUREKA_ENABLED='false'` |
| `Could not find artifact com.example:entity-service` | Run Step 2 (`clean install`) first |
| Test page says `WebSocket error` | socket-service isn't running, or the page was opened from another port |
| Test page shows no `RIDE REQUEST` | The driver id in the page must match the `driverId` sent in the notify call |
| IDE shows red `getEmail()` or `builder()` | Enable Lombok annotation processing in the IDE; Maven itself builds fine |
| A test fails | Copy the test name and the first error lines |

## Docker (final step, not needed yet)

`docker-compose.yml` currently covers MySQL, Eureka and auth only. The final stage extends it to every service, switches them to MySQL, Redis (`SPRING_PROFILES_ACTIVE=redis` for location) and Kafka, and adds a Docker test guide.

## Design notes

See [docs/HLD.md](docs/HLD.md) for diagrams, the data model, the flows and the trade-offs I made on purpose.
