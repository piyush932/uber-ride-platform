# API demo

Import postman/Uber-Ride-Platform-Demo.postman_collection.json into Postman. The baseUrl environment variable defaults to http://127.0.0.1:8080. Start the backend or Compose stack without gateway rate limiting.

Run the whole collection in order without artificial delays. It creates unique accounts, captures tokens and ids, puts a driver online, creates a booking, finds its offer by booking id, accepts, starts, completes and reads history. Offers expire after 20 seconds; slow manual execution can legitimately return 409.

```powershell
npm install -g newman
.\scripts\run-postman-demo.ps1
```

The demo uses fixed coordinates 26.34567, 76.45678; other online drivers there can interfere. The randomized PowerShell driver-flow suite remains the regression test for rejection and expiry.

On failure the runner stops and may skip cleanup. Use saved variables to DELETE /api/v1/locations/drivers/{{driverId}} and cancel PENDING/CONFIRMED bookings with POST /api/v1/bookings/{{bookingId}}/cancel and {"reason":"DEMO_CLEANUP"}. Inspect IN_PROGRESS rides manually rather than rewriting them. Accounts and bookings persist.

Tokens/passwords are runtime collection variables; clear them before export. Use disposable local data, never production. This collection is prepared against existing API contracts but has not been runtime-executed here.
