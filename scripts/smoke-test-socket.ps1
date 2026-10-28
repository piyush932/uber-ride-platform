# Smoke test for the REST side of socket-service. Start it first (Eureka is optional).
# The live WebSocket part is tested in the browser: http://localhost:8083/test.html
# Usage (from the repo root):  .\scripts\smoke-test-socket.ps1
param([string]$Base = 'http://localhost:8083')

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

function Call([string]$Method, [string]$Path, $Body = $null) {
    $params = @{ Method = $Method; Uri = ($Base + $Path); UseBasicParsing = $true; ErrorAction = 'Stop' }
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

$health = Call 'GET' '/actuator/health'
if ($health.Status -eq 0) {
    Write-Host ('socket-service is not reachable at ' + $Base + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'health returns 200' $health.Status 200
Check 'health status is UP' $health.Json.status 'UP'

$ride = Call 'POST' '/api/v1/socket/rides/notify' @{ driverId = 7; bookingId = 101; passengerId = 1; pickupLatitude = 12.9716; pickupLongitude = 77.5946 }
Check 'notify driver returns 202' $ride.Status 202

$bad = Call 'POST' '/api/v1/socket/rides/notify' @{ bookingId = 101 }
Check 'notify without driverId returns 400' $bad.Status 400

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
