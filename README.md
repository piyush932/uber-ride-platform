# Uber Ride Platform

A full-stack ride-booking application built with Spring Boot and React. Passengers request rides from a map; drivers manage availability, respond to timed offers and run trips from a separate dashboard.

## Features

- Separate passenger and driver accounts with JWT authentication and gateway role rules.
- Redis GEO nearby-driver discovery with expiring location updates.
- Persisted ride offers with a 20-second response window, rejection, expiry and reassignment across up to five drivers.
- Trip start/completion, cancellation, history and review APIs.
- Idempotency keys, transactional updates, optimistic locking and circuit breakers.
- Docker Compose for the frontend, gateway, backend services, MySQL and Redis.

## Stack

Java 17 target, Spring Boot 3.5.16, Spring Cloud 2025.0.3, Maven, MySQL, Redis, React 18, Vite and Leaflet. Socket-service supports STOMP notifications; React screens currently poll. Kafka publishing is optional and experimental.

## Run with Docker

Install Docker Desktop with Compose v2. Stop local applications using ports 5173, 8080 and 8761.

```powershell
Copy-Item .env.example .env
# Replace MYSQL_ROOT_PASSWORD and JWT_SECRET in .env.
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Do not overwrite an existing .env or commit credentials. Docker configuration is implemented; container runtime verification is still pending.

| Entry point | Address |
|---|---|
| Passenger app | http://127.0.0.1:5173 |
| Driver portal | http://127.0.0.1:5173/driver/login |
| Gateway | http://127.0.0.1:8080 |
| Eureka | http://127.0.0.1:8761 |

Compose does not publish internal service/database ports. Docker MySQL uses a separate volume from a locally installed database.

## Try a ride

1. Sign up as a driver, select a position and go online.
2. Open the passenger app in another browser session, choose a nearby pickup and request a ride.
3. Accept, start and complete the trip in the driver dashboard.
4. Inspect both histories. Use two drivers to try rejection and reassignment.

## Build and test

```powershell
# Stop running local JARs before clean builds on Windows.
.\scripts\mvn.ps1 clean install
cd frontend
npm install
npm run build
cd ..
.\scripts\smoke-test-driver-flow.ps1 -Gateway http://127.0.0.1:8080 -IncludeExpiry
```

The local expiry-inclusive smoke suite was reported passing with 71 checks and zero failures. This is sequential API coverage, not proof of concurrency safety or Docker verification. Tests remain part of the project.

## Documentation

- [Architecture](docs/HLD.md)
- [Docker operations](docs/DOCKER.md)
- [Postman demo](docs/API-DEMO.md)
- [Publishing checklist](docs/PUBLISHING.md)

## Scope and next work

The core ride-booking workflow is implemented. This is a portfolio/local-development application, not a production service. Passenger ownership, atomic driver reservation, internal service authentication, controlled migrations and live UI push remain future work. Do not expose the running application to untrusted users before addressing those gaps.
