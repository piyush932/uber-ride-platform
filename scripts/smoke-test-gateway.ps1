# Smoke test for api-gateway. Start eureka, auth, location, socket, booking, review and gateway first.
# Usage (from the repo root):  .\scripts\smoke-test-gateway.ps1
param(
    [string]$Gateway = 'http://localhost:8080'
)

$script:total = 0
$script:failed = 0

function Check([string]$Name, $Actual, $Expected) {
    $script:total++
    if ($Actual -eq $Expected) {
        Write-Host ('PASS  ' + $Name) -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host ('FAIL  ' + $Name + ' (expected ' + $Expected + ', got ' + $Actual + ')') -ForegroundColor Red
    }
}

function Call([string]$Method, [string]$Url, $Body = $null, [string]$Token = '') {
    $params = @{ Method = $Method; Uri = $Url; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($Token) {
        $params.Headers = @{ Authorization = ('Bearer ' + $Token) }
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json)
    }
    try {
        $resp = Invoke-WebRequest @params
        $json = $null
        if ($resp.Content) { $json = $resp.Content | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = $json }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $code; Json = $null }
    }
}

$health = Call 'GET' ($Gateway + '/actuator/health')
if ($health.Status -eq 0) {
    Write-Host ('api-gateway is not reachable at ' + $Gateway + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'gateway health returns 200' $health.Status 200

$run = [string][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$email = ('rider' + $run + '@example.com')

$signup = Call 'POST' ($Gateway + '/api/v1/auth/signup') @{ name = 'Rider'; email = $email; password = 'secret123' }
Check 'signup through gateway returns 201' $signup.Status 201

$login = Call 'POST' ($Gateway + '/api/v1/auth/login') @{ email = $email; password = 'secret123' }
Check 'login through gateway returns 200' $login.Status 200
$token = $login.Json.token

$noToken = Call 'GET' ($Gateway + '/api/v1/bookings?passengerId=1')
Check 'bookings without a token returns 401' $noToken.Status 401

$badToken = Call 'GET' ($Gateway + '/api/v1/bookings?passengerId=1') -Token 'not-a-token'
Check 'bookings with a garbage token returns 401' $badToken.Status 401

$me = Call 'GET' ($Gateway + '/api/v1/auth/me') -Token $token
Check 'me through gateway with token returns 200' $me.Status 200
Check 'me returns the logged in user' $me.Json.email $email

$driverPut = Call 'PUT' ($Gateway + '/api/v1/locations/drivers/9001') @{ latitude = 12.9720; longitude = 77.5950 } -Token $token
Check 'driver online through gateway returns 204' $driverPut.Status 204

$key = ('idem-' + $run)
$booking = Call 'POST' ($Gateway + '/api/v1/bookings') @{ passengerId = 1; pickupLatitude = 12.9716; pickupLongitude = 77.5946 } -Token $token
# add the idempotency header manually
try {
    $resp = Invoke-WebRequest -Method Post -Uri ($Gateway + '/api/v1/bookings') `
        -Headers @{ Authorization = ('Bearer ' + $token); 'Idempotency-Key' = $key } `
        -ContentType 'application/json' `
        -Body '{"passengerId":1,"pickupLatitude":12.9716,"pickupLongitude":77.5946}' `
        -UseBasicParsing -ErrorAction Stop
    $bookingId = ($resp.Content | ConvertFrom-Json).id
    $script:total++
    Write-Host 'PASS  booking created through gateway' -ForegroundColor Green
} catch {
    $script:total++
    $script:failed++
    Write-Host 'FAIL  booking created through gateway' -ForegroundColor Red
}

# same key again must return the same booking, not create a new one
try {
    $resp2 = Invoke-WebRequest -Method Post -Uri ($Gateway + '/api/v1/bookings') `
        -Headers @{ Authorization = ('Bearer ' + $token); 'Idempotency-Key' = $key } `
        -ContentType 'application/json' `
        -Body '{"passengerId":1,"pickupLatitude":12.9716,"pickupLongitude":77.5946}' `
        -UseBasicParsing -ErrorAction Stop
    $same = (($resp2.Content | ConvertFrom-Json).id -eq $bookingId)
    Check 'same idempotency key returns the same booking' $same $true
} catch {
    Check 'same idempotency key returns the same booking' $false $true
}

$reviews = Call 'GET' ($Gateway + '/api/v1/reviews?driverId=9001') -Token $token
Check 'reviews through gateway returns 200' $reviews.Status 200

Call 'DELETE' ($Gateway + '/api/v1/locations/drivers/9001') -Token $token | Out-Null

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
