# Nearby drivers on the passenger map

Status: code prepared; npm build and browser testing still required.

## Scope

Frontend only; no backend changes or new dependencies. Pickup/destination selection, place-name lookup and cancellation remain unchanged. A map badge shows loading, login required, zero drivers, a nearby count, or an availability error. Driver markers show the latest successful nearby response. Errors clear markers rather than silently displaying stale locations.

The API query uses a 5 km radius and a limit of 50. At that cap the display says 50+; the exact total is unknown. This is a display radius, not a new guarantee about the booking service's configurable matching radius. Markers are reported positions, not driver eligibility guarantees or simulated movement. No ETA, driving route, driver login, acceptance or rejection is added.

Requests start 400 ms after pickup changes and repeat 10 seconds after each response. Old requests are aborted on point changes or map unmount. Session expiry stops polling. Only changes to selected pickup/destination or the geolocation button recenter the map; driver responses do not. Requests continue in a background tab while the map remains mounted.

## Run

From repo root: git pull, cd frontend, npm run build, npm run dev. Keep the backend running and log in at http://localhost:5173. No Maven rebuild is needed.

## Browser tests

1. Signed out: select a pickup; see Log in to see nearby drivers and no protected location polling.
2. Log in, select a pickup: see Checking nearby drivers, then an availability count or No drivers nearby.
3. Register numeric test driver 9001 at the exact pickup using the existing console location-update snippet. Within the next refresh a car marker should appear. Keep refreshing its position if testing beyond the location TTL.
4. Click the car: see its id and distance without changing the selected pickup/destination. Cars at the same point as pickup may overlap; zoom or click their edge to inspect.
5. Pan the map; wait for a driver refresh. The map should not snap back.
6. Stop the demo position timer and DELETE /api/v1/locations/drivers/9001. After a successful refresh its marker disappears. Other drivers may remain.
7. Move pickup far away; previous markers must clear while the new query loads.
8. Block /api/v1/locations/nearby in DevTools. Expect Driver availability unavailable, not No drivers nearby. Unblock; the next successful poll restores the display.
9. Navigate away or log out; inspect Network to confirm polling stops after cleanup (an in-flight request may be cancelled).
10. Regression: book with a nearby test driver, cancel the pending ride, verify CANCELLED persists, and check place names and My rides.

## Deployment limitations

Public production rollout needs backend identity/role enforcement and an explicit policy for exposing driver locations. Exact positions and ids are shown here only for development. Driver presence still comes from test tooling until the driver dashboard is built.
