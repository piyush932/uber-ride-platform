# Smoke test for location-service. Start it first (Eureka is optional).
# Usage (from the repo root):  .\scripts\smoke-test-location.ps1
param([string]$Base = 'http://localhost:8082')

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

function DriverIds($result) {
    return @($result.Json | Where-Object { $_ } | ForEach-Object { $_.driverId })
}

$health = Call 'GET' '/actuator/health'
if ($health.Status -eq 0) {
    Write-Host ('location-service is not reachable at ' + $Base + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'health returns 200' $health.Status 200

$run = [string][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$near = 'near-' + $run
$mid = 'mid-' + $run
$far = 'far-' + $run
$prefix = '/api/v1/locations/drivers/'

# search centre: 12.9716, 77.5946. near is about 0.06 km away, mid about 1.1 km, far about 28 km
$r1 = Call 'PUT' ($prefix + $near) @{ latitude = 12.9720; longitude = 77.5950 }
Check 'save near driver returns 204' $r1.Status 204
$r2 = Call 'PUT' ($prefix + $mid) @{ latitude = 12.9800; longitude = 77.6000 }
Check 'save mid driver returns 204' $r2.Status 204
$r3 = Call 'PUT' ($prefix + $far) @{ latitude = 13.1986; longitude = 77.7066 }
Check 'save far driver returns 204' $r3.Status 204

$small = Call 'GET' '/api/v1/locations/nearby?lat=12.9716&lng=77.5946&radiusKm=5&limit=50'
Check 'nearby (5 km) returns 200' $small.Status 200
$ours = @(DriverIds $small | Where-Object { $_ -in @($near, $mid, $far) })
Check 'nearest driver comes first' $ours[0] $near
Check 'second nearest comes next' $ours[1] $mid
Check 'far driver is outside 5 km' ($ours -contains $far) $false

$wide = Call 'GET' '/api/v1/locations/nearby?lat=12.9716&lng=77.5946&radiusKm=50&limit=50'
Check 'nearby (50 km) returns 200' $wide.Status 200
Check 'far driver is inside 50 km' ((DriverIds $wide) -contains $far) $true

$badPut = Call 'PUT' ($prefix + $near) @{ latitude = 91; longitude = 77 }
Check 'latitude 91 is rejected with 400' $badPut.Status 400
$badGet = Call 'GET' '/api/v1/locations/nearby?lat=200&lng=77'
Check 'search with latitude 200 is rejected with 400' $badGet.Status 400

$del = Call 'DELETE' ($prefix + $near)
Check 'taking a driver offline returns 204' $del.Status 204
$after = Call 'GET' '/api/v1/locations/nearby?lat=12.9716&lng=77.5946&radiusKm=5&limit=50'
Check 'offline driver no longer appears' ((DriverIds $after) -contains $near) $false

# clean up
Call 'DELETE' ($prefix + $mid) | Out-Null
Call 'DELETE' ($prefix + $far) | Out-Null

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
