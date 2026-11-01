# Stage 9A-1: driver authentication backend

Implemented, not compiled or browser-tested by the assistant. This increment intentionally precedes driver UI and presence. Keep testing passenger flows unchanged. No acceptance/rejection or trip-lifecycle changes are included.

## Endpoints via gateway :8080

- POST /api/v1/auth/signup/driver with name, email, password: 201 profile (no password).
- POST /api/v1/auth/login/driver with email, password: 200 bearer token.
- GET /api/v1/auth/driver/me with driver bearer token: 200 profile.

Driver accounts use the already existing shared Driver entity in auth-service's database. Passenger and driver email uniqueness are separate. JWT DRIVER role and userId are server-issued. Driver id and email are checked against the database on auth-service requests. Unknown roles are rejected. Existing subject-only passenger tokens remain supported as a transitional compatibility measure; passenger user-id claims have not been migrated yet.

The gateway allows driver tokens only on /auth/driver/me in this stage. Passenger tokens cannot use that endpoint. Driver tokens cannot use passenger bookings, locations or reviews yet. Driver presence endpoints and permissions will be added in a later stage; do not use the passenger console simulation with a driver token.

## Build and restart

From repository root run git pull and .\scripts\mvn.ps1 clean install, without skipping tests. Restart auth-service and api-gateway using your existing profile/environment settings and their rebuilt jars. Existing database content must not be deleted. Five tests are added; if the previous total was 68, expect 73, but trust actual Maven output. These are service tests, not full security integration tests.

## PowerShell test

```powershell
$base = 'http://localhost:8080'
$email = 'driver-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + '@example.com'
$signup = @{ name='Demo Driver'; email=$email; password='secret123' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/signup/driver" -ContentType 'application/json' -Body $signup
$body = @{ email=$email; password='secret123' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login/driver" -ContentType 'application/json' -Body $body
$headers = @{ Authorization=('Bearer ' + $login.token) }
Invoke-RestMethod -Uri "$base/api/v1/auth/driver/me" -Headers $headers
```

Expected: id, name, email, role DRIVER. Use a different email for repeated signup tests or expect duplicate-email rejection.

Negative checks (PowerShell will throw on expected non-2xx responses):
- Driver token on GET /api/v1/auth/me: 403.
- Driver token on GET /api/v1/bookings?passengerId=1: 403.
- Passenger token on GET /api/v1/auth/driver/me: 403.
- No token or malformed token on driver profile: 401.
- Wrong driver password: 401.
- Same email registered as both roles must never interchange the two identities.

Regression: run .\scripts\smoke-test-gateway.ps1 and passenger login, map availability, booking, cancellation and history in the browser. Driver UI is not included yet.

## Remaining security work

This is not production-ready. Direct access to other backend ports bypasses gateway role restrictions; restrict network access and add service-level checks. Booking passenger ownership, authenticated offer ownership, protected WebSocket subscriptions, driver eligibility/approval, account disablement, rate limits, JWT audience/issuer and passenger-id token migration remain outstanding. Driver self-registration grants a development driver account, not verified commercial-driver approval.
