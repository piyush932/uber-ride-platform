# Uber Ride Platform

A local-first ride-hailing project for practising microservices: authentication, discovery, nearby-driver lookup, booking orchestration, timed offers, trip lifecycle and reviews.

Stack: Java 17 target (JDK 25 used locally), Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven multi-module, React 18, Vite, Leaflet and axios.

## Project status

| Phase | Scope | Status |
|---|---|---|
| 1 | Maven modules and helper scripts | Implemented |
| 2 | Eureka, entity library, passenger auth/JWT | Implemented |
| 3 | Driver positions, TTL and nearby search | Implemented |
| 4 | Socket-service STOMP notifications | Implemented |
| 5 | Booking orchestration and events | Implemented, extended in Phase 9 |
| 6 | Reviews for completed bookings | Implemented |
| 7 | Gateway JWT rules, circuit breakers, optional Redis rate limit | Implemented |
| 8 | Passenger UI, map booking, status polling and history | Implemented |
| 9 | Driver accounts/portal, heartbeat, offers, reject/expiry/reassignment, start/complete | Implemented; see verification status below |
| Next | Security/concurrency hardening, live UI push and complete Docker deployment | Pending |

The backend build has been confirmed locally. The smoke-test output demonstrated the main sequential driver flows, but four single-offer assertions failed in the expiry-inclusive run. The array-return fix is in commit f53014e; a zero-failure rerun has not yet been explicitly reported. Build success does not establish browser behavior, infrastructure compatibility or concurrent dispatch safety.

See [the HLD](docs/HLD.md) for architecture, diagrams and consistency limitations. This is a development project, not a production-ready public deployment.

## Services and repository

| Component | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| location-service | 8082 |
| socket-service | 8083 |
| booking-service | 8084 |
| review-service | 8085 |
| eureka-server | 8761 |
| Vite frontend | 5173 |

- backend/: Maven parent and service modules; entity-service is a shared library.
- frontend/: separate passenger and driver layouts and sessions.
- scripts/: Maven/start helpers and smoke tests.
- docs/HLD.md: implemented architecture and roadmap.
- postman/: earlier-phase request collections.
- docker-compose.yml: partial deployment, not the complete platform.

## Prerequisites

Use the project's Java 17 target with your configured JDK, Node/npm for frontend, and PowerShell on Windows. Set JAVA_HOME and check java -version and node -v. The Maven helper downloads Maven if required. MySQL and Redis are optional for the in-memory defaults; use your local stores for persistent-profile testing. Map tiles and address lookup need internet access. Kafka is not required for the default flow.

## Build the backend

Run from the repository root. Stop running backend JARs first: Windows cannot replace a JAR held by a running application.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
git pull origin main
.\scripts\mvn.ps1 clean install
Get-ChildItem backend\*\target\*.jar
```

Require BUILD SUCCESS and zero test failures. Do not use -DskipTests for verification. Seven runnable services plus the entity library are built.

For IntelliJ, import backend/pom.xml, configure the JDK and Lombok annotation processing, and run each service Application class. Stop old IDE instances before starting rebuilt JARs.

## MySQL upgrade for Phase 9

Back up valuable local data and stop booking-service before changing its schema. Inspect first:

```sql
USE uber_booking;
SHOW CREATE TABLE booking;
```

Only if status is a native enum missing IN_PROGRESS, widen it:

```sql
ALTER TABLE booking MODIFY COLUMN status
ENUM('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL;
```

Do not overwrite a different column definition without adapting the migration. A fresh/H2 database needs no manual enum change. The ride_offer table is included in booking-service entity scanning and created by ddl-auto=update. Controlled Flyway migrations are still planned.

## Start the backend

### In-memory defaults

```powershell
.\scripts\start-all.ps1
```

Wait for startup and check http://localhost:8761 for registered services.

### Local MySQL and Redis

Open a separate PowerShell window in the repo root for each service. Set environment variables only in the intended window. Start Eureka first:

```powershell
java -jar backend\eureka-server\target\eureka-server.jar
```

For auth, booking and review, set mysql and DB_PASSWORD before launching the corresponding JAR (repeat in separate windows):

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:DB_PASSWORD = 'your-local-mysql-password'
# Auth window:
java -jar backend\auth-service\target\auth-service.jar
# Booking window, after setting the same variables there:
# java -jar backend\booking-service\target\booking-service.jar
# Review window, after setting the same variables there:
# java -jar backend\review-service\target\review-service.jar
```

Databases are uber_auth, uber_booking and uber_review. Other local settings include DB_USER and DB_PORT.

Location window, with local Redis running:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'redis'
java -jar backend\location-service\target\location-service.jar
```

Socket window:

```powershell
java -jar backend\socket-service\target\socket-service.jar
```

Gateway window, without its rate-limit profile for smoke testing:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
java -jar backend\api-gateway\target\api-gateway.jar
```

Location may still use Redis while gateway rate limiting is disabled. To test gateway rate limiting separately, restart only the gateway with SPRING_PROFILES_ACTIVE=redis. Keep JWT_SECRET consistent across auth and gateway; do not use the development default outside an isolated local environment.

Setting EUREKA_ENABLED=false disables registration/discovery participation for individual services, but the gateway's lb:// routes still require service-instance resolution. It is not a complete gateway standalone configuration.

## Start and build the frontend

```powershell
cd frontend
npm install
npm run build
npm run dev
```

- Passenger: http://localhost:5173/
- Driver: http://localhost:5173/driver/login

Vite proxies /api to http://localhost:8080 without rewriting the path. VITE_API_URL changes the axios API base when supplied; it does not change the proxy target in vite.config.js. Prefer the default same-origin proxy locally.

Use separate browser profiles or a normal and incognito window for watching passenger and driver together. Driver storage uses driver_token/driver_user; passenger storage uses token/user. Two different drivers need isolated browser storage.

## Manual Phase 9 test checklist

1. Driver: sign up or log in, click the map or use the dashboard's geolocation button, then toggle Offline to Online. Verify PUT /api/v1/locations/drivers/{id} returns 204 and repeats approximately every 5 seconds.
2. Passenger: log in, choose pickup within 5 km of the driver and a destination, then request a ride. It starts PENDING.
3. Driver: a New ride request card should appear after dashboard polling (approximately 3 seconds plus request time). Accept before the server deadline.
4. Passenger: confirm the status becomes CONFIRMED and shows the assigned driver.
5. Driver: Start trip. Passenger sees IN_PROGRESS and no Cancel button. Complete trip; both sides see COMPLETED/history.
6. Reject/reassign: put a second driver online nearby using an isolated browser session. Book again; the nearest driver rejects and the other driver receives the next offer.
7. No drivers left: reject from every eligible driver; expect CANCELLED / NO_DRIVER_AVAILABLE.
8. Expiry: with only one eligible driver online, request another ride and do nothing. The 20-second offer expires; cancellation follows the sweep, normally within a few more seconds.
9. Cancellation: cancel a PENDING ride as passenger; the offer disappears and cannot be accepted. CONFIRMED is also cancellable, IN_PROGRESS is not.
10. Offline: toggle offline and verify DELETE /api/v1/locations/drivers/{id} returns 204. Check histories on both sides.

Both UIs poll status rather than subscribe to WebSocket push. The map line joins selected points; it is not a driving route. Address labels are browser lookups and are not stored with bookings. Closing a tab/logging out is not a guaranteed immediate location deletion; TTL is the fallback.

## Automated Phase 9 smoke tests

Use a disposable local environment with services running, and gateway rate limiting disabled. Run from the repo root:

```powershell
.\scripts\smoke-test-driver-flow.ps1
.\scripts\smoke-test-driver-flow.ps1 -IncludeExpiry
```

Expected successful totals: 37 checks / 0 failed, or 41 checks / 0 failed with expiry. The optional run waits approximately 26 seconds for the expiry scenario. If you changed offer TTL, pass the same value using -OfferSeconds.

The script creates a passenger, two drivers and bookings. It tests missing/wrong-role tokens, own-location rules, blocked legacy actions, nearest-driver offer, accept/start/complete/history, foreign-driver rejection, in-progress cancel denial, reassignment, exhausted candidates, offer withdrawal and optional expiry. It uses Delhi coordinates; other drivers online there can interfere. Accounts/bookings remain in local databases. Location cleanup normally runs at the end but can be skipped by a crash. Never run it against production.

BookingServiceTest, OfferServiceTest, DriverRideControllerTest and JwtAuthFilterTest are part of the Maven build. These mock-based tests and sequential smoke tests do not prove concurrent driver-reservation safety.

## Earlier-phase tests

```powershell
.\scripts\smoke-test-auth.ps1
.\scripts\smoke-test-location.ps1
.\scripts\smoke-test-socket.ps1
.\scripts\smoke-test-booking.ps1
.\scripts\smoke-test-review.ps1
.\scripts\smoke-test-gateway.ps1
```

Earlier collections/scripts may assume a passenger token can write driver locations or a driver can accept without a live offer. Those assumptions are obsolete after Phase 9; do not treat their old expected totals as current compatibility guarantees. Use the driver-flow script for the new flow. Some Windows PowerShell 5.1 health checks may also misread Actuator response content.

## API and matching summary

Driver signup/login: POST /api/v1/auth/signup/driver and /api/v1/auth/login/driver. Profile: GET /api/v1/auth/driver/me. Driver actions use JWT-derived gateway headers, not a driver id supplied in the body.

| Driver API | Purpose |
|---|---|
| GET /api/v1/driver/dashboard | Active ride and live offers |
| POST /api/v1/driver/offers/{id}/accept | Confirm a live, owned offer |
| POST /api/v1/driver/offers/{id}/reject | Reject and try the next candidate |
| POST /api/v1/driver/rides/{id}/start | CONFIRMED to IN_PROGRESS |
| POST /api/v1/driver/rides/{id}/complete | IN_PROGRESS to COMPLETED |
| GET /api/v1/driver/rides | Assigned-driver ride history |

Matching examines up to 20 nearby candidates, skips tried or busy drivers and offers to one eligible driver at a time. Defaults: BOOKING_SEARCH_RADIUS_KM=5, BOOKING_OFFER_TTL_SECONDS=20, BOOKING_OFFER_MAX_ATTEMPTS=5, BOOKING_OFFER_SWEEP_MS=2000. No initial candidate gives NO_DRIVERS_NEARBY / 409; exhausted reassignment gives NO_DRIVER_AVAILABLE. Successful creation stores the idempotency key; failed creation does not.

## Troubleshooting

| Symptom | Check |
|---|---|
| Cannot delete a target JAR during clean | Stop its service terminal/IDE process, then rebuild. Do not kill every Java process. |
| Script execution disabled | Set ExecutionPolicy to Bypass for the current process only. |
| Missing JAR | Check the first build error; require BUILD SUCCESS. |
| Frontend API/network error | Gateway must be running on 8080; use localhost:5173 for the configured browser origin. |
| Missing Bearer token during driver login | Verify login URL is /api/v1/auth/login/driver, not /auth/driver/login. Pull current frontend code. |
| Driver location returns 403 | Use a driver token and its own numeric id. |
| Driver dashboard 404 | Restart rebuilt gateway; both YAML configurations must route /api/v1/driver/** to booking-service. |
| Driver dashboard 503 | Check booking-service registration, startup and circuit-breaker fallback. |
| Starting a trip gives Data truncated | Inspect MySQL booking.status and apply the IN_PROGRESS upgrade if needed. |
| Booking creation 409 | No eligible nearby driver, possibly because all are busy. |
| Offer accept 409 | Offer expired/closed or booking changed; refresh dashboard. |
| Smoke test 429 | Restart gateway without its redis profile; location-service can keep redis. |
| One-offer assertions fail but accepts succeed | Pull f53014e or later for the PowerShell array-return fix. |
| Review says booking must be COMPLETED | Finish the trip using the assigned driver. |
| Port already in use | Stop the old identified service instance before restarting. |

## Known limitations and next work

- Passenger booking/review identity checks are not fully bound to the authenticated caller; booking ownership enforcement is pending.
- Anyone can register as a driver; approval and identity verification are absent.
- Direct service ports trust headers and bypass the gateway. Keep this project local; service-level authentication and network isolation are pending.
- Numeric-only gateway location-write matching leaves non-numeric location path authorization to be hardened.
- Busy-driver checks are not atomic reservations across concurrent bookings. Optimistic locking protects conflicting row updates, not every cross-booking invariant.
- Multiple schedulers lack distributed coordination and unique live-offer guarantees. Older stranded PENDING bookings fall outside the 15-minute recovery window.
- Legacy internal completion can bypass the strict driver start-before-complete flow.
- JWT refresh/revocation, scoped/payload-validated idempotency, event outbox, pagination, migrations and browser automation remain pending.
- WebSocket UI push, persisted place labels and stronger heartbeat/offline race handling are future work.

Docker Compose remains partial. Completing deployment with MySQL, Redis and Kafka, infrastructure integration tests and an operational runbook follows application hardening.

See [docs/HLD.md](docs/HLD.md) for the detailed architecture and trade-offs.
