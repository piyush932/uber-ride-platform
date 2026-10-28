# Smoke test for booking-service. Start eureka, location, socket and booking first.
# Usage (from the repo root):  .\scripts\smoke-test-booking.ps1
param(
    [string]$BookingBase = 'http://localhost:8084',
    [string]$LocationBase = 'http://localhost:8082'
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

function Call([string]$Method, [string]$Url, $Body = $null) {
    $params = @{ Method = $Method; Uri = $Url; UseBasicParsing = $true; ErrorAction = 'Stop' }
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

$health = Call 'GET' ($BookingBase + '/actuator/health')
if ($health.Status -eq 0) {
    Write-Host ('booking-service is not reachable at ' + $BookingBase + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'health returns 200' $health.Status 200

# A driver must be online near the pickup point. Driver ids must be numeric for booking.
$driverId = 9001
$put = Call 'PUT' ($LocationBase + '/api/v1/locations/drivers/' + $driverId) @{ latitude = 12.9720; longitude = 77.5950 }
Check 'driver 9001 is online (location returns 204)' $put.Status 204

$booking = Call 'POST' ($BookingBase + '/api/v1/bookings') @{ passengerId = 1; pickupLatitude = 12.9716; pickupLongitude = 77.5946; dropLatitude = 12.9352; dropLongitude = 77.6245 }
Check 'create booking returns 201' $booking.Status 201
Check 'new booking is PENDING' $booking.Json.status 'PENDING'
Check 'nearest driver was picked' $booking.Json.candidateDriverId $driverId

$bookingId = $booking.Json.id
$accept = Call 'POST' ($BookingBase + '/api/v1/bookings/' + $bookingId + '/accept') @{ driverId = $driverId }
Check 'driver accept returns 200' $accept.Status 200
Check 'booking is CONFIRMED' $accept.Json.status 'CONFIRMED'
Check 'booking now has the driver' $accept.Json.driverId $driverId

$again = Call 'POST' ($BookingBase + '/api/v1/bookings/' + $bookingId + '/accept') @{ driverId = 9002 }
Check 'second accept returns 409' $again.Status 409

$cancel = Call 'POST' ($BookingBase + '/api/v1/bookings/' + $bookingId + '/cancel') @{ reason = 'smoke test' }
Check 'cancel returns 200' $cancel.Status 200
Check 'booking is CANCELLED' $cancel.Json.status 'CANCELLED'

# no driver online now, so a new booking must be refused
Call 'DELETE' ($LocationBase + '/api/v1/locations/drivers/' + $driverId) | Out-Null
$empty = Call 'POST' ($BookingBase + '/api/v1/bookings') @{ passengerId = 1; pickupLatitude = 12.9716; pickupLongitude = 77.5946 }
Check 'booking with no drivers nearby returns 409' $empty.Status 409

$bad = Call 'POST' ($BookingBase + '/api/v1/bookings') @{ pickupLatitude = 12.9716; pickupLongitude = 77.5946 }
Check 'booking without passengerId returns 400' $bad.Status 400

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
