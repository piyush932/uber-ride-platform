# Local development only. Gateway rate limiting must be disabled.
# .\scripts\smoke-test-driver-flow.ps1 -IncludeExpiry
param(
    [string]$Gateway = 'http://localhost:8080',
    [switch]$IncludeExpiry,
    [ValidateRange(5,300)][int]$OfferSeconds = 20,
    [ValidateRange(5,120)][int]$WaitSeconds = 12
)
$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0
$script:drivers = @()
$script:bookingIds = @()
$script:last = ''
$script:passengerToken = ''
$run = [guid]::NewGuid().ToString('N')
$password = 'SmokeOnly-42!x'
# Random local-test coordinates avoid leftovers at the former fixed Delhi point.
$lat = 26.0 + (Get-Random -Minimum 0 -Maximum 100000) / 100000.0
$lng = 76.0 + (Get-Random -Minimum 0 -Maximum 100000) / 100000.0

function Safe-Text($Value) {
    $text = $Value | ConvertTo-Json -Depth 12 -Compress
    $text = $text -replace '(?i)"(token|accessToken|refreshToken|password)"\s*:\s*"[^"]*"', '"$1":"[REDACTED]"'
    if ($text.Length -gt 2000) { $text = $text.Substring(0,2000) }
    return $text
}
function Api([string]$Method, [string]$Path, [string]$Token = '', $Body = $null, [int]$Expected = 200) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($Method -eq 'POST' -and $Path -eq '/api/v1/bookings') {
        $headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
    }
    $options = @{ Uri = "$Gateway$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 15; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $options.Body = $Body | ConvertTo-Json -Compress
        $options.ContentType = 'application/json'
    }
    $code = 0
    $text = ''
    try {
        $response = Invoke-WebRequest @options
        $code = [int]$response.StatusCode
        $text = $response.Content
    } catch {
        if ($null -ne $_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($_.ErrorDetails.Message) { $text = $_.ErrorDetails.Message }
        elseif ($code -gt 0) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $text = $reader.ReadToEnd()
                $reader.Dispose()
            } catch { $text = 'Response body unavailable' }
        } else { $text = 'Transport failure or timeout; check gateway connectivity' }
    }
    $data = $null
    if ($text) { try { $data = $text | ConvertFrom-Json } catch { $data = [string]$text } }
    if ($Path -match '/auth/(login|signup)') { $summary = '[authentication body omitted]' }
    else { $summary = Safe-Text $data }
    $script:last = "$Method $Path -> HTTP $code; body=$summary"
    if ($code -ne $Expected) { throw "Expected HTTP $Expected. $script:last" }
    return $data
}
function Assert([string]$Name, [bool]$Condition) {
    if (!$Condition) { throw "$Name. Last response: $script:last" }
    $script:passed++
    Write-Host "  PASS  $Name" -ForegroundColor Green
}
function Wait-For([string]$Name, [scriptblock]$Probe, [int]$Seconds = $WaitSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        $result = & $Probe
        if ($result) {
            Assert $Name $true
            return $result
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Name. Last response: $script:last"
}
function New-Account([string]$Label, [bool]$Driver) {
    $suffix = ''
    if ($Driver) { $suffix = '/driver' }
    $email = "$Label-$run@example.com"
    $null = Api 'POST' "/api/v1/auth/signup$suffix" '' @{name=$Label; email=$email; password=$password} 201
    $login = Api 'POST' "/api/v1/auth/login$suffix" '' @{email=$email; password=$password}
    if (!$login.token) { throw 'Authentication returned no token' }
    $profilePath = '/api/v1/auth/me'
    if ($Driver) { $profilePath = '/api/v1/auth/driver/me' }
    $me = Api 'GET' $profilePath $login.token
    if (!$me.id) { throw 'Profile returned no id' }
    return [pscustomobject]@{Id=[long]$me.id; Token=$login.token}
}
function Position($Driver, [bool]$Online, [double]$Offset = 0) {
    $path = "/api/v1/locations/drivers/$($Driver.Id)"
    if ($Online) { $null = Api 'PUT' $path $Driver.Token @{latitude=($lat+$Offset); longitude=$lng} 204 }
    else { $null = Api 'DELETE' $path $Driver.Token $null 204 }
}
function Nearby {
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $a = $lat.ToString($culture); $b = $lng.ToString($culture)
    $items = @(Api 'GET' "/api/v1/locations/nearby?lat=$a&lng=$b&radiusKm=5&limit=50" $script:passengerToken)
    return ,@($items | Where-Object { $null -ne $_ })
}
function Area([int]$ExpectedCount) {
    $items = Nearby
    $allowed = @($script:drivers | ForEach-Object { [string]$_.Id })
    $foreign = @($items | Where-Object { [string]$_.driverId -notin $allowed })
    if ($foreign.Count) { throw "Unexpected nearby drivers: $(Safe-Text $foreign). Use an isolated test environment." }
    Assert "Nearby test-driver count is $ExpectedCount" ($items.Count -eq $ExpectedCount)
}
function Booking($Id) { return Api 'GET' "/api/v1/bookings/$Id" $script:passengerToken }
function Dashboard($Driver) { return Api 'GET' '/api/v1/driver/dashboard' $Driver.Token }
function Offers($Driver, $BookingId) {
    $dash = Dashboard $Driver
    return ,@($dash.offers | Where-Object { $null -ne $_ -and $_.bookingId -eq $BookingId })
}
function Wait-Offer($Driver, $BookingId) {
    return Wait-For "driver $($Driver.Id) owns one live offer for booking $BookingId" {
        $items = Offers $Driver $BookingId
        if ($items.Count -eq 1 -and $items[0].secondsRemaining -gt 0) { return $items[0] }
    }
}
function Wait-State($Id, [string]$State, [int]$Seconds = $WaitSeconds) {
    return Wait-For "booking $Id becomes $State" {
        $b = Booking $Id
        if ($b.status -eq $State) { return $b }
    } $Seconds
}
function Create-Booking($ExpectedDriver) {
    $b = Api 'POST' '/api/v1/bookings' $script:passengerToken @{
        passengerId=$script:passenger.Id; pickupLatitude=$lat; pickupLongitude=$lng
        dropLatitude=($lat+0.01); dropLongitude=($lng+0.01)
    }
    if ($b.id) { $script:bookingIds += $b.id }
    Assert 'Booking created as PENDING' ($b.id -and $b.status -eq 'PENDING')
    Assert "Candidate is expected driver $($ExpectedDriver.Id)" ($b.candidateDriverId -eq $ExpectedDriver.Id)
    return $b
}
function Prepare {
    foreach ($d in $script:drivers) {
        $dash = Dashboard $d
        Assert "Driver $($d.Id) is idle before scenario" (!$dash.activeRide -and @($dash.offers).Count -eq 0)
        Position $d $false
    }
    Position $script:drivers[0] $true
    Position $script:drivers[1] $true 0.001
    Area 2
}
function Offer-Action($Driver, $Offer, [string]$Action, [int]$Expected = 200) {
    if (!$Offer.id) { throw 'Refusing to call offer endpoint with a missing id' }
    return Api 'POST' "/api/v1/driver/offers/$($Offer.id)/$Action" $Driver.Token $null $Expected
}
function Finish-Ride($Driver, $Id) {
    $r = Api 'POST' "/api/v1/driver/rides/$Id/start" $Driver.Token
    Assert 'Start returns IN_PROGRESS' ($r.status -eq 'IN_PROGRESS')
    $null = Wait-State $Id 'IN_PROGRESS'
    $null = Api 'POST' "/api/v1/bookings/$Id/cancel" $script:passengerToken @{reason='test'} 409
    Assert 'In-progress cancel is denied' $true
    $r = Api 'POST' "/api/v1/driver/rides/$Id/complete" $Driver.Token
    Assert 'Complete returns COMPLETED' ($r.status -eq 'COMPLETED')
    $null = Wait-State $Id 'COMPLETED'
}

try {
    Write-Host "Local smoke test via $Gateway at $lat, $lng. Accounts/bookings persist."
    $script:passenger = New-Account 'passenger' $false
    $script:passengerToken = $script:passenger.Token
    $script:drivers += New-Account 'driver1' $true
    $script:drivers += New-Account 'driver2' $true
    $d1 = $script:drivers[0]; $d2 = $script:drivers[1]
    Assert 'Passenger and drivers authenticated' ($d1.Id -ne $d2.Id)
    Area 0

    Write-Host 'Role checks'
    $null = Api 'GET' '/api/v1/driver/dashboard' '' $null 401
    Assert 'Missing token denied' $true
    $null = Api 'GET' '/api/v1/driver/dashboard' $script:passengerToken $null 403
    Assert 'Passenger denied on driver API' $true
    $null = Api 'POST' '/api/v1/bookings' $d1.Token @{passengerId=$script:passenger.Id; pickupLatitude=$lat; pickupLongitude=$lng} 403
    Assert 'Driver denied on passenger booking API' $true
    $null = Api 'PUT' "/api/v1/locations/drivers/$($d1.Id)" $script:passengerToken @{latitude=$lat; longitude=$lng} 403
    Assert 'Passenger location write denied' $true
    $null = Api 'PUT' "/api/v1/locations/drivers/$($d2.Id)" $d1.Token @{latitude=$lat; longitude=$lng} 403
    Assert 'Foreign driver location write denied' $true
    $null = Api 'POST' '/api/v1/bookings/1/accept' $script:passengerToken @{driverId=$d1.Id} 403
    $null = Api 'POST' '/api/v1/bookings/1/complete' $script:passengerToken $null 403
    Assert 'Legacy actions denied to passenger' $true

    Write-Host 'Happy path'
    Prepare
    $b = Create-Booking $d1
    $o = Wait-Offer $d1 $b.id
    Assert 'Countdown is within configured TTL' ($o.secondsRemaining -le $OfferSeconds)
    Assert 'Second driver has no offer for this booking' ((Offers $d2 $b.id).Count -eq 0)
    $r = Offer-Action $d1 $o 'accept'
    Assert 'Acceptance returns CONFIRMED' ($r.status -eq 'CONFIRMED')
    $r = Wait-State $b.id 'CONFIRMED'
    Assert 'Assigned driver is correct' ($r.driverId -eq $d1.Id)
    Assert 'Dashboard has this active booking' ((Dashboard $d1).activeRide.id -eq $b.id)
    $null = Api 'POST' "/api/v1/driver/rides/$($b.id)/start" $d2.Token $null 403
    Assert 'Foreign driver start denied' $true
    Finish-Ride $d1 $b.id
    $history = @(Api 'GET' '/api/v1/driver/rides' $d1.Token)
    Assert 'History contains completed booking' (@($history | Where-Object {$_.id -eq $b.id}).Count -eq 1)

    Write-Host 'Reject and reassign'
    Prepare
    $b = Create-Booking $d1
    $o = Wait-Offer $d1 $b.id
    $null = Offer-Action $d1 $o 'reject'
    $next = Wait-Offer $d2 $b.id
    $null = Offer-Action $d1 $o 'accept' 409
    Assert 'Rejected offer cannot be accepted' $true
    $r = Offer-Action $d2 $next 'accept'
    Assert 'Second driver accepts reassigned ride' ($r.status -eq 'CONFIRMED' -and $r.driverId -eq $d2.Id)
    Finish-Ride $d2 $b.id

    Write-Host 'No drivers left'
    Prepare
    $b = Create-Booking $d1
    $o = Wait-Offer $d1 $b.id
    $null = Offer-Action $d1 $o 'reject'
    $o = Wait-Offer $d2 $b.id
    Area 2
    $null = Offer-Action $d2 $o 'reject'
    $r = Wait-State $b.id 'CANCELLED'
    Assert 'Exhaustion reason is NO_DRIVER_AVAILABLE' ($r.cancelReason -eq 'NO_DRIVER_AVAILABLE')

    Write-Host 'Passenger cancellation'
    Prepare
    $b = Create-Booking $d1
    $o = Wait-Offer $d1 $b.id
    $null = Api 'POST' "/api/v1/bookings/$($b.id)/cancel" $script:passengerToken @{reason='CANCELLED_BY_PASSENGER'}
    $null = Wait-State $b.id 'CANCELLED'
    $null = Wait-For 'Cancelled booking offer disappears' { if ((Offers $d1 $b.id).Count -eq 0) { return $true } }
    $null = Offer-Action $d1 $o 'accept' 409
    Assert 'Withdrawn offer cannot be accepted' $true

    if ($IncludeExpiry) {
        Write-Host 'Expiry'
        Prepare
        Position $d2 $false
        Area 1
        $b = Create-Booking $d1
        $o = Wait-Offer $d1 $b.id
        $r = Wait-State $b.id 'CANCELLED' ($OfferSeconds+$WaitSeconds+10)
        Assert 'Expiry exhaustion reason is NO_DRIVER_AVAILABLE' ($r.cancelReason -eq 'NO_DRIVER_AVAILABLE')
        Assert 'Expired booking has no live offer' ((Offers $d1 $b.id).Count -eq 0)
        $null = Offer-Action $d1 $o 'accept' 409
        Assert 'Expired offer cannot be accepted' $true
    }
} catch {
    $script:failed++
    Write-Host "FAIL (suite aborted to prevent cascading failures): $($_.Exception.Message)" -ForegroundColor Red
} finally {
    foreach ($d in $script:drivers) {
        try { Position $d $false } catch {
            $script:failed++; Write-Warning "Driver $($d.Id) location cleanup failed: $($_.Exception.Message)"
        }
    }
    foreach ($id in $script:bookingIds) {
        try {
            $b = Booking $id
            if ($b.status -in @('PENDING','CONFIRMED')) {
                $null = Api 'POST' "/api/v1/bookings/$id/cancel" $script:passengerToken @{reason='SMOKE_TEST_CLEANUP'}
            } elseif ($b.status -eq 'IN_PROGRESS') {
                Write-Warning "Booking $id remains IN_PROGRESS; inspect it manually. Cleanup will not fabricate completion."
            }
        } catch {
            $script:failed++; Write-Warning "Booking $id cleanup failed: $($_.Exception.Message)"
        }
    }
}
Write-Host "$script:passed checks passed; $script:failed failures. Totals differ from the original 37/41-check script."
if ($script:failed -gt 0) { exit 1 }
exit 0
