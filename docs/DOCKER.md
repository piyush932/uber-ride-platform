# Running the platform in Docker

This runs every service, MySQL, Redis and the React app in containers. It is a local-development stack, not a production deployment (see Limits).

## What is exposed

| Address | What | Why published |
|---|---|---|
| http://127.0.0.1:5173 | React passenger app and `/driver` portal (nginx) | Browser entry point; proxies `/api` to the gateway |
| http://127.0.0.1:8080 | API gateway | Smoke tests and API clients |
| http://127.0.0.1:8761 | Eureka dashboard | Checking registrations |

MySQL, Redis and every backend service stay on the internal Docker network. Nothing from your host can reach them directly, so the direct-port bypass described in the HLD does not apply to this stack. Only the loopback address is bound, so other devices on your network cannot connect either.

## Prerequisites

- Docker Desktop (Compose v2) with BuildKit, which is on by default.
- About 4 GB of memory available to Docker: seven JVMs, MySQL and Redis run together.
- Stop any locally running services first. Docker needs ports 8080, 5173 and 8761.

## First run (PowerShell, repo root)

```powershell
git pull origin main
Copy-Item .env.example .env
```

Edit `.env` and replace both values:

- `MYSQL_ROOT_PASSWORD`: any password for the container database.
- `JWT_SECRET`: at least 32 characters. Generate one with:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Compose refuses to start if either value is missing. `.env` is git-ignored; never commit it.

```powershell
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

The first build takes several minutes because each service compiles its Maven modules. Later builds reuse the Maven cache. Wait until every service shows `healthy` in `docker compose ps` (about a minute or two after the containers start), then:

- Open http://127.0.0.1:8761 and confirm AUTH-SERVICE, LOCATION-SERVICE, SOCKET-SERVICE, BOOKING-SERVICE, REVIEW-SERVICE and API-GATEWAY are registered.
- Open http://127.0.0.1:5173 for the passenger app and http://127.0.0.1:5173/driver/login for the driver portal.

## End-to-end check against the containers

The gateway runs without rate limiting by default, which the smoke test needs:

```powershell
.\scripts\smoke-test-driver-flow.ps1 -Gateway http://127.0.0.1:8080 -IncludeExpiry
```

Expect `0 failures`. Use 127.0.0.1 rather than localhost because the port is bound to the IPv4 loopback address.

## Everyday commands

```powershell
docker compose logs -f booking-service     # follow one service
docker compose restart api-gateway         # restart one service
docker compose up -d --build booking-service   # rebuild and restart after a code change
docker compose down                        # stop; the database keeps its data
docker compose down -v                     # stop and DELETE the database and all test data
docker compose exec mysql mysql -uroot -p  # open a MySQL shell (it asks for the root password)
```

## Optional settings

Set these in `.env`:

- `GATEWAY_PROFILES=redis` turns on gateway rate limiting. The smoke tests will then receive 429 responses.
- `BOOKING_OFFER_TTL_SECONDS`, `BOOKING_OFFER_MAX_ATTEMPTS`, `LOCATION_TTL_SECONDS` tune dispatch and location expiry.
- Kafka (experimental): set `BOOKING_PROFILES=mysql,kafka` and run `docker compose --profile kafka up -d`. Booking events are then published to the `booking-events` topic on a Redpanda container. The Kafka publisher has not been exercised against a real broker yet, and no service consumes the topic, so leave this off unless you are testing it. The image tag is `latest`; pin a specific version before relying on it.

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `Set MYSQL_ROOT_PASSWORD in .env` or `Set JWT_SECRET in .env` | `.env` is missing or a value is empty. Copy `.env.example` and fill both values |
| MySQL stays unhealthy, or services log `Access denied` | A volume from an earlier Compose file still holds the old root password. Run `docker compose down -v` and start again (this deletes that data) |
| `port is already allocated` | A local service or old container uses 8080, 5173 or 8761. Stop it |
| A service keeps restarting | `docker compose logs <service>` and read the first exception. Most often MySQL or Eureka was not ready yet; it retries automatically |
| Frontend loads but API calls fail with 502 | The gateway is not healthy yet. Check `docker compose ps` and `docker compose logs api-gateway` |
| Login works but every call returns 401 | `JWT_SECRET` changed after tokens were issued. Log in again |
| Smoke test cannot connect | Pass `-Gateway http://127.0.0.1:8080` |
| Build is slow or runs out of memory | Raise Docker Desktop memory to 4 GB or more; build one service at a time with `docker compose build <service>` |

## Limits

- Credentials are passed to containers as environment variables, visible to anyone who can run `docker inspect` on your machine. Fine for local development; use a secrets manager for any shared environment.
- Services connect to MySQL as `root`. A least-privilege user per database would be the next step.
- There is no TLS, backup, log shipping or resource limit configuration.
- Tests are skipped during image builds (`-DskipTests`). Run `.\scripts\mvn.ps1 clean install` locally to execute them.
- The frontend image uses `npm install` because the repository has no `package-lock.json`. Commit one and change the Dockerfile to `npm ci` for reproducible builds.
- Passenger booking ownership checks, atomic driver reservation and internal service authentication are still pending; the private network reduces exposure but does not replace them.
