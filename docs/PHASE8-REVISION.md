# Passenger frontend revision

Status: implemented, awaiting local build and browser verification. This is not deployment-ready yet.

## Changes

- Public booking preview with login/signup calls to action; protected history and ride screens.
- Session validation through /auth/me, logout, and expired-session handling.
- Signup now sends the required name. Login sends the new token explicitly on /me.
- Responsive sidebar/map layout, editable pickup/destination fields and geolocation recentering.
- Same idempotency key retained for retries until a selected point changes. This does not cover page reloads.
- Sequential status polling stops at terminal status; network failures retry more slowly.
- The dotted map segment is not road routing. Driver UI and passenger WebSocket updates remain deferred.

## Local setup

From the repository root run git pull, then cd frontend, npm install, npm run build, npm run dev.
Open http://localhost:5173. Restart Vite after pulling because its proxy configuration changed.
The default API URL is now relative. Vite proxies /api to http://localhost:8080, avoiding cross-origin browser requests during development. Remove any old VITE_API_URL=http://localhost:8080 override from frontend/.env or .env.local before restarting Vite.
This proxy is development-only. A deployment must serve /api through a reverse proxy or use a configured API URL with a validated CORS policy. The existing gateway YAML is unchanged by this revision.

## Verification checklist

1. Clear old session data using browser storage tools; load /book. Login/signup must be visible.
2. Request a ride signed out; expect login, not a protected request.
3. Create an account including name; confirm login and /me succeed.
4. Reload with a valid session; the account should be restored.
5. Pick pickup and destination, then edit each independently. Geolocation denial must show a usable message.
6. Put a numeric-id driver online near the pickup before requesting a ride. Refresh the driver location if its TTL expires.
7. Create a ride, inspect its status, cancel, and check history. For completion, first accept using the existing driver/socket tooling.
8. Confirm terminal rides stop polling. Test logout and an expired token.
9. Test narrow mobile, tablet, and desktop viewport widths, keyboard focus, and map controls.
10. Run the backend gateway smoke script and Maven tests separately to check for regressions.

## Remaining work

No browser or npm execution was available during this change. Run the checks above before marking the revision verified. Existing React Router future-flag warnings are not resolved in this commit; they do not cause the CORS error. Legacy page components remain in the repository but App.jsx no longer imports them. A later cleanup can split the new screens into individual files.
Before public deployment: backend per-user authorization, HTTPS, secret management, token-storage review, dependency audit, reproducible lockfile, production reverse proxy, SPA fallback routing, and a map tile provider/policy appropriate for the expected traffic are required.
