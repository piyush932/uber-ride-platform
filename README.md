# Uber Ride Platform

A ride-hailing backend I'm building phase by phase to practise real microservice problems: service discovery, stateless auth, geospatial search, real-time push and event-driven flows. Every phase is small enough to run and test on its own.

Stack: Java (builds to Java 17 bytecode, runs on 17 to 25), Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven.

The plan is: **run and test everything locally first** (no Docker, no database server), then switch to Docker at the end.

## Where the project is right now

| Phase | What | Status |
|-------|------|--------|
| 1 | Project skeleton, Maven multi-module build, helper scripts | Done |
| 2 | Eureka registry, shared entity library, Auth service (signup, login, JWT) with unit tests | Done |
| 3 | Location service (nearby drivers) | Next |
| 4 | Socket service (WebSocket/STOMP push to drivers) | Next |
| 5 | Booking service (orchestrates location + socket, events) | Planned |
| 6 | Review service | Planned |
| 7 | API gateway, circuit breakers, tracing | Planned |
| 8 | React frontend (booking form + live map) | Planned |
| Final | Docker Compose with MySQL, Redis, Kafka | Planned |

The README is updated with every phase. [docs/HLD.md](docs/HLD.md) describes the full target design and marks what is built.

## What the current code solves

- **Who is calling?** Passengers sign up and log in, and receive a signed JWT. Protected endpoints need `Authorization: Bearer <token>`.
- **Where do services live?** Services register with Eureka instead of using hard-coded host names.
- **One domain model, many services.** `entity-service` is a plain Java library holding the JPA entities (`Passenger`, `Driver`, `Booking`) so later services don't each re-declare them.
- **Safe credential handling.** Passwords are stored as BCrypt hashes, the API is stateless, and bad input returns clean JSON errors instead of stack traces.

## Repo layout

```
uber-ride-platform/
  scripts/
    mvn.ps1                 runs Maven (downloads it into .tools/ if you don't have it)
    smoke-test-auth.ps1     automated end-to-end check of the auth API
  backend/                  Maven multi-module build (parent pom.xml)
    entity-service/         shared JPA entities (library, not runnable)
    eureka-server/          service registry on :8761
    auth-service/           signup / login / JWT on :8081
    Dockerfile              one multi-stage Dockerfile used for every service (final step)
  docker-compose.yml        final step, not needed for local runs
  docs/HLD.md               architecture and design decisions
  postman/                  importable test collection
```

## What you need on your laptop

- A JDK, version 17 or newer (JDK 25 works). `java -version` must print a version, and `JAVA_HOME` should point at the JDK folder.
- Nothing else. Maven is downloaded for you by `scripts/mvn.ps1`, and the local database is in-memory (H2).
- Optional: Postman, IntelliJ IDEA.

## Run it locally (Windows PowerShell)

Open PowerShell in the repo root (the folder that contains `backend` and `scripts`).

**Step 1: allow the helper scripts for this window only**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

**Step 2: build everything once** (the first run downloads Maven and the dependencies, so allow a few minutes)

```powershell
.\scripts\mvn.ps1 clean install -DskipTests
```

Expected: the last lines say `BUILD SUCCESS`, and these files exist:
`backend\eureka-server\target\eureka-server.jar` and `backend\auth-service\target\auth-service.jar`.

**Step 3: start Eureka (window 1, leave it running)**

```powershell
java -jar backend\eureka-server\target\eureka-server.jar
```

Expected: the log ends with `Started EurekaServerApplication`. Open http://localhost:8761 and you see the Eureka dashboard with no instances yet.

**Step 4: start auth (window 2, leave it running)**

Open a second PowerShell in the repo root:

```powershell
java -jar backend\auth-service\target\auth-service.jar
```

Expected: `Started AuthServiceApplication`. After about 30 seconds `AUTH-SERVICE` appears on http://localhost:8761. By default auth uses an in-memory H2 database, so data is wiped each time you restart it.

No Eureka running? Start auth with `$env:EUREKA_ENABLED='false'` set first in that window. It works the same without it.

## How to test Phase 1-2

### A. One command (recommended)

Open a third PowerShell window in the repo root:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\smoke-test-auth.ps1
```

Expected output: 14 green `PASS` lines and then `14 checks, 0 failed`. Any `FAIL` line tells you which request returned a different status than expected.

### B. Manual checks

The same calls by hand, so you can see the real responses:

```powershell
$base = 'http://localhost:8081/api/v1/auth'

# 1. signup -> 201 and a body with id, name, email (no password)
Invoke-RestMethod -Method Post -Uri "$base/signup" -ContentType 'application/json' `
  -Body (@{ name = 'Asha'; email = 'asha@example.com'; password = 'secret123' } | ConvertTo-Json)

# 2. login -> token
$login = Invoke-RestMethod -Method Post -Uri "$base/login" -ContentType 'application/json' `
  -Body (@{ email = 'asha@example.com'; password = 'secret123' } | ConvertTo-Json)
$login

# 3. protected endpoint with the token -> the same passenger
Invoke-RestMethod -Uri "$base/me" -Headers @{ Authorization = "Bearer $($login.token)" }
```

Expected for step 2: an object with `token` (long string starting `eyJ`), `tokenType = Bearer`, `expiresInMs = 3600000`.

Failure paths. In Windows PowerShell these throw an error, and the status code is in the error message:

| Request | Expected status |
|---------|-----------------|
| Signup again with the same email | 409 |
| Signup with `email = 'nope'` or a 3 character password | 400 |
| Login with the wrong password | 401 |
| Login with an email that was never registered | 401 (same message, so emails can't be probed) |
| `GET /me` with no header | 401 |
| `GET /me` with `Authorization: Bearer garbage` | 401 |

### C. Token expiry

Stop auth (Ctrl+C) and restart it with a 5 second token life:

```powershell
$env:JWT_EXPIRY_MS = '5000'
java -jar backend\auth-service\target\auth-service.jar
```

Log in and call `/me` straight away (200). Wait 6 seconds and call it again (401). Then close that window so the setting doesn't linger.

### D. Unit tests (no servers needed)

```powershell
.\scripts\mvn.ps1 -pl auth-service -am test
```

Expected: `Tests run: 16, Failures: 0, Errors: 0` and `BUILD SUCCESS` (5 in JwtServiceTest, 5 in AuthServiceTest, 6 in AuthControllerTest). Warnings from Mockito about a dynamic agent are harmless.

### E. Postman

Import `postman/Uber-Ride-Platform.postman_collection.json`, then Run collection. All 10 requests should be green. The collection uses a new email on each run, so you can re-run it as often as you like.

## Using IntelliJ instead

Open the repo folder, right-click `backend/pom.xml` and choose Add as Maven Project. Set the project SDK to your JDK (17 or newer) and enable the Lombok plugin and annotation processing. Then run `EurekaServerApplication` and `AuthServiceApplication` with the green arrow beside each `main` method.

## Troubleshooting

| What you see | Likely cause and fix |
|---|---|
| `running scripts is disabled on this system` | Run the `Set-ExecutionPolicy -Scope Process ...` line from Step 1 in that window |
| Script can't download Maven | Check your internet or proxy, or install Maven yourself and make sure `mvn -v` works; the script uses it when found |
| `release version 17 not supported` | Your `java` is older than 17. Install JDK 17 or newer and set `JAVA_HOME` |
| `Port 8081 (or 8761) was already in use` | An old instance is still running. Close it, or start with `--server.port=9081` |
| Repeating `Connection refused ... 8761` in auth's log | Eureka isn't running. Start it or set `$env:EUREKA_ENABLED='false'` |
| `Could not find artifact com.example:entity-service` | Run Step 2 (`clean install`) first |
| IDE shows red `getEmail()` or `builder()` | Enable Lombok annotation processing in the IDE; Maven itself builds fine |
| A test fails | Copy the test name and the first error lines |

## Docker (final step, not needed yet)

Once all phases pass locally, `docker compose up --build -d` runs everything with MySQL instead of H2 (it sets `SPRING_PROFILES_ACTIVE=mysql`). Redis and Kafka start with `--profile full`. A full Docker test guide is added in the final stage.

## Design notes

See [docs/HLD.md](docs/HLD.md) for diagrams, the data model, the auth flow and the trade-offs I made on purpose (for example, the shared entity library).
