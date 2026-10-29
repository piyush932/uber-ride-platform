# Smoke test for review-service. Start eureka, location, socket, booking and review first.
# Usage (from the repo root):  .\scripts\smoke-test-review.ps1
param(
    [string]$ReviewBase = 'http://localhost:8085',
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

$health = Call 'GET' ($ReviewBase + '/actuator/health')
if ($health.Status -eq 0) {
    Write-Host ('review-service is not reachable at ' + $ReviewBase + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'health returns 200' $health.Status 200

$driverId = 9001
Call 'PUT' ($LocationBase + '/api/v1/locations/drivers/' + $driverId) @{ latitude = 12.9720; longitude = 77.5950 } | Out-Null

$booking = Call 'POST' ($BookingBase + '/api/v1/bookings') @{ passengerId = 1; pickupLatitude = 12.9716; pickupLongitude = 77.5946 }
Check 'create booking returns 201' $booking.Status 201
$bookingId = $booking.Json.id

Call 'POST' ($BookingBase + '/api/v1/bookings/' + $bookingId + '/accept') @{ driverId = $driverId } | Out-Null

$early = Call 'POST' ($ReviewBase + '/api/v1/reviews') @{ bookingId = $bookingId; passengerId = 1; rating = 5; comment = 'too early' }
Check 'review before completion returns 409' $early.Status 409

$complete = Call 'POST' ($BookingBase + '/api/v1/bookings/' + $bookingId + '/complete')
Check 'complete booking returns 200' $complete.Status 200
Check 'booking is COMPLETED' $complete.Json.status 'COMPLETED'

$review = Call 'POST' ($ReviewBase + '/api/v1/reviews') @{ bookingId = $bookingId; passengerId = 1; rating = 5; comment = 'Great ride' }
Check 'create review returns 201' $review.Status 201
Check 'review is linked to the driver' $review.Json.driverId $driverId

$again = Call 'POST' ($ReviewBase + '/api/v1/reviews') @{ bookingId = $bookingId; passengerId = 1; rating = 4; comment = 'again' }
Check 'second review for the same booking returns 409' $again.Status 409

$summary = Call 'GET' ($ReviewBase + '/api/v1/reviews?driverId=' + $driverId)
Check 'driver rating summary returns 200' $summary.Status 200
Check 'driver has 1 review' $summary.Json.totalReviews 1
Check 'driver average rating is 5' $summary.Json.averageRating 5

$bad = Call 'POST' ($ReviewBase + '/api/v1/reviews') @{ bookingId = 999999; passengerId = 1; rating = 5 }
Check 'review for an unknown booking returns 404' $bad.Status 404

$invalid = Call 'POST' ($ReviewBase + '/api/v1/reviews') @{ bookingId = $bookingId; passengerId = 1; rating = 6 }
Check 'rating 6 returns 400' $invalid.Status 400

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
