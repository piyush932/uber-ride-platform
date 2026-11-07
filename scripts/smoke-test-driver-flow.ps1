# Phase 9 driver flow, tested through the API gateway.
#   .\scripts\smoke-test-driver-flow.ps1
#   .\scripts\smoke-test-driver-flow.ps1 -IncludeExpiry      (adds a run that waits for an offer to expire)
# Start every service first. Run the gateway WITHOUT the redis profile, otherwise its rate limit
# (10 requests burst) can answer 429 to this script.
param(
    [string]$Gateway = 'http://localhost:8080',
    [switch]$IncludeExpiry,
    [int]$OfferSeconds = 20
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0

function Check([string]$Name, [bool]$Condition, $Detail = '') {
    if ($Condition) {
        $script:passed++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "  FAIL  $Name  $Detail" -ForegroundColor Red
    }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token = '', $Body = $null, [hashtable]$ExtraHeaders = @{})
    $headers = @{}
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Uri = "$Gateway$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Compress)
        $params['ContentType'] = 'application/json'
    }
    $code = 0
    $text = $null
    try {
        $response = Invoke-WebRequest @params
        $code = [int]$response.StatusCode
        $text = $response.Content
    } catch {
        $failure = $_.Exception.Response
        if ($null -eq $failure) { return [pscustomobject]@{ Status = 0; Data = $null } }
        $code = [int]$failure.StatusCode
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $text = $_.ErrorDetails.Message
        } else {
            try {
                $reader = New-Object System.IO.StreamReader($failure.GetResponseStream())
                $text = $reader.ReadToEnd()
            } catch { $text = $null }
        }
    }
    $data = $null
    if ($text) {
        try { $data = $text | ConvertFrom-Json } catch { $data = $text }
    }
    [pscustomobject]@{ Status = $code; Data = $data }
}

# A pickup far from the usual test area, so drivers left online by earlier manual tests are not nearby.
$pickupLat = 28.6139
$pickupLng = 77.2090
$run = Get-Date -Format 'HHmmssfff'
$password = 'Passw0rd!x'

function New-Driver([string]$Label) {
    $email = "d$Label$run@example.com"
    Invoke-Api 'POST' '/api/v1/auth/signup/driver' '' @{ name = "Smoke Driver $Label"; email = $email; password = $password } | Out-Null
    $login = Invoke-Api 'POST' '/api/v1/auth/login/driver' '' @{ email = $email; password = $password }
    $token = $login.Data.token
    $me = Invoke-Api 'GET' '/api/v1/auth/driver/me' $token
    [pscustomobject]@{ Token = $token; Id = [long]$me.Data.id }
}

function Set-Online($Driver, [double]$Lat, [double]$Lng) {
    Invoke-Api 'PUT' "/api/v1/locations/drivers/$($Driver.Id)" $Driver.Token @{ latitude = $Lat; longitude = $Lng }
}

function Set-Offline($Driver) {
    Invoke-Api 'DELETE' "/api/v1/locations/drivers/$($Driver.Id)" $Driver.Token
}

function Get-Dash($Driver) {
    (Invoke-Api 'GET' '/api/v1/driver/dashboard' $Driver.Token).Data
}

function Get-Offers($Driver) {
    $dash = Get-Dash $Driver
    @($dash.offers)
}

function New-Booking {
    $body = @{
        passengerId = $script:passengerId
        pickupLatitude = $pickupLat
        pickupLongitude = $pickupLng
        dropLatitude = $pickupLat + 0.01
        dropLongitude = $pickupLng + 0.01
    }
    Invoke-Api 'POST' '/api/v1/bookings' $script:passengerToken $body @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
}

function Get-Booking($Id) {
    (Invoke-Api 'GET' "/api/v1/bookings/$Id" $script:passengerToken).Data
}

function Set-BothOnline {
    Set-Online $driver1 $pickupLat $pickupLng | Out-Null
    Set-Online $driver2 ($pickupLat + 0.0005) $pickupLng | Out-Null
}

Write-Host "Setup: one passenger and two drivers through $Gateway"
$passengerEmail = "p$run@example.com"
Invoke-Api 'POST' '/api/v1/auth/signup' '' @{ name = 'Smoke Passenger'; email = $passengerEmail; password = $password } | Out-Null
$passengerLogin = Invoke-Api 'POST' '/api/v1/auth/login' '' @{ email = $passengerEmail; password = $password }
$script:passengerToken = $passengerLogin.Data.token
$passengerMe = Invoke-Api 'GET' '/api/v1/auth/me' $script:passengerToken
$script:passengerId = [long]$passengerMe.Data.id
$driver1 = New-Driver '1'
$driver2 = New-Driver '2'
Check 'passenger and both drivers could sign up and log in' ($script:passengerToken -and $driver1.Token -and $driver2.Token -and $driver1.Id -ne $driver2.Id)

Write-Host ''
Write-Host 'Role checks'
Check 'no token on the driver API gives 401' ((Invoke-Api 'GET' '/api/v1/driver/dashboard').Status -eq 401)
Check 'passenger on the driver API gives 403' ((Invoke-Api 'GET' '/api/v1/driver/dashboard' $script:passengerToken).Status -eq 403)
$driverBooking = Invoke-Api 'POST' '/api/v1/bookings' $driver1.Token @{ passengerId = 1; pickupLatitude = $pickupLat; pickupLongitude = $pickupLng }
Check 'driver on the passenger booking route gives 403' ($driverBooking.Status -eq 403)
$passengerWrite = Invoke-Api 'PUT' "/api/v1/locations/drivers/$($driver1.Id)" $script:passengerToken @{ latitude = $pickupLat; longitude = $pickupLng }
Check 'passenger cannot write a driver location' ($passengerWrite.Status -eq 403)
$foreignWrite = Invoke-Api 'PUT' "/api/v1/locations/drivers/$($driver2.Id)" $driver1.Token @{ latitude = $pickupLat; longitude = $pickupLng }
Check 'driver cannot write another driver location' ($foreignWrite.Status -eq 403)
$legacyAccept = Invoke-Api 'POST' '/api/v1/bookings/1/accept' $script:passengerToken @{ driverId = $driver1.Id }
Check 'passenger cannot call legacy accept' ($legacyAccept.Status -eq 403)
$legacyComplete = Invoke-Api 'POST' '/api/v1/bookings/1/complete' $script:passengerToken
Check 'passenger cannot call legacy complete' ($legacyComplete.Status -eq 403)

Write-Host ''
Write-Host 'Happy path: offer, accept, start, complete'
Set-BothOnline
$created = New-Booking
$bookingId = $created.Data.id
Check 'booking is created as PENDING' ($created.Status -eq 200 -and $created.Data.status -eq 'PENDING') "status=$($created.Status)"
$offers1 = Get-Offers $driver1
Check 'nearest driver gets exactly one offer' ($offers1.Count -eq 1 -and $offers1[0].bookingId -eq $bookingId)
Check 'the other driver gets no offer yet' ((Get-Offers $driver2).Count -eq 0)
Check 'offer has a countdown' ($offers1.Count -eq 1 -and $offers1[0].secondsRemaining -gt 0 -and $offers1[0].secondsRemaining -le $OfferSeconds)
$accepted = Invoke-Api 'POST' "/api/v1/driver/offers/$($offers1[0].id)/accept" $driver1.Token
Check 'driver accepts the offer' ($accepted.Status -eq 200 -and $accepted.Data.status -eq 'CONFIRMED') "status=$($accepted.Status)"
$seen = Get-Booking $bookingId
Check 'passenger sees CONFIRMED with the driver' ($seen.status -eq 'CONFIRMED' -and $seen.driverId -eq $driver1.Id)
Check 'driver dashboard shows the active ride' ((Get-Dash $driver1).activeRide.id -eq $bookingId)
$foreignStart = Invoke-Api 'POST' "/api/v1/driver/rides/$bookingId/start" $driver2.Token
Check 'another driver cannot start the ride' ($foreignStart.Status -eq 403)
$started = Invoke-Api 'POST' "/api/v1/driver/rides/$bookingId/start" $driver1.Token
Check 'driver starts the trip' ($started.Status -eq 200 -and $started.Data.status -eq 'IN_PROGRESS')
Check 'passenger sees IN_PROGRESS' ((Get-Booking $bookingId).status -eq 'IN_PROGRESS')
$lateCancel = Invoke-Api 'POST' "/api/v1/bookings/$bookingId/cancel" $script:passengerToken @{ reason = 'test' }
Check 'passenger cannot cancel a trip in progress' ($lateCancel.Status -eq 409)
$completed = Invoke-Api 'POST' "/api/v1/driver/rides/$bookingId/complete" $driver1.Token
Check 'driver completes the trip' ($completed.Status -eq 200 -and $completed.Data.status -eq 'COMPLETED')
Check 'passenger sees COMPLETED' ((Get-Booking $bookingId).status -eq 'COMPLETED')
$history = Invoke-Api 'GET' '/api/v1/driver/rides' $driver1.Token
Check 'driver history lists the ride' (@($history.Data | Where-Object { $_.id -eq $bookingId }).Count -eq 1)

Write-Host ''
Write-Host 'Reject, then reassign to the next driver'
Set-BothOnline
$created = New-Booking
$bookingId = $created.Data.id
$offer = (Get-Offers $driver1)[0]
Check 'first driver gets the offer' ($null -ne $offer -and $offer.bookingId -eq $bookingId)
$rejected = Invoke-Api 'POST' "/api/v1/driver/offers/$($offer.id)/reject" $driver1.Token
Check 'first driver rejects' ($rejected.Status -eq 200)
Check 'booking is still PENDING' ((Get-Booking $bookingId).status -eq 'PENDING')
Check 'first driver has no offer left' ((Get-Offers $driver1).Count -eq 0)
$next = Get-Offers $driver2
Check 'second driver now has the offer' ($next.Count -eq 1 -and $next[0].bookingId -eq $bookingId)
$reuse = Invoke-Api 'POST' "/api/v1/driver/offers/$($offer.id)/accept" $driver1.Token
Check 'rejected offer cannot be accepted' ($reuse.Status -eq 409)
$accepted = Invoke-Api 'POST' "/api/v1/driver/offers/$($next[0].id)/accept" $driver2.Token
Check 'second driver accepts' ($accepted.Status -eq 200 -and $accepted.Data.status -eq 'CONFIRMED')
Check 'booking belongs to the second driver' ((Get-Booking $bookingId).driverId -eq $driver2.Id)
Invoke-Api 'POST' "/api/v1/driver/rides/$bookingId/start" $driver2.Token | Out-Null
Invoke-Api 'POST' "/api/v1/driver/rides/$bookingId/complete" $driver2.Token | Out-Null

Write-Host ''
Write-Host 'No drivers left'
Set-BothOnline
$created = New-Booking
$bookingId = $created.Data.id
$offer1 = (Get-Offers $driver1)[0]
Invoke-Api 'POST' "/api/v1/driver/offers/$($offer1.id)/reject" $driver1.Token | Out-Null
$offer2 = (Get-Offers $driver2)[0]
Check 'second driver is tried after the first rejects' ($null -ne $offer2 -and $offer2.bookingId -eq $bookingId)
Invoke-Api 'POST' "/api/v1/driver/offers/$($offer2.id)/reject" $driver2.Token | Out-Null
$final = Get-Booking $bookingId
Check 'booking is CANCELLED when nobody is left' ($final.status -eq 'CANCELLED')
Check 'cancel reason is NO_DRIVER_AVAILABLE' ($final.cancelReason -eq 'NO_DRIVER_AVAILABLE') "reason=$($final.cancelReason)"

Write-Host ''
Write-Host 'Passenger cancel withdraws the open offer'
Set-BothOnline
$created = New-Booking
$bookingId = $created.Data.id
$offer = (Get-Offers $driver1)[0]
Check 'driver has an open offer' ($null -ne $offer)
$cancelled = Invoke-Api 'POST' "/api/v1/bookings/$bookingId/cancel" $script:passengerToken @{ reason = 'CANCELLED_BY_PASSENGER' }
Check 'passenger cancels a pending ride' ($cancelled.Status -eq 200 -and $cancelled.Data.status -eq 'CANCELLED')
Check 'the open offer is gone from the dashboard' ((Get-Offers $driver1).Count -eq 0)
$withdrawn = Invoke-Api 'POST' "/api/v1/driver/offers/$($offer.id)/accept" $driver1.Token
Check 'the withdrawn offer cannot be accepted' ($withdrawn.Status -eq 409)

if ($IncludeExpiry) {
    Write-Host ''
    Write-Host "Expiry (waits about $($OfferSeconds + 6) seconds)"
    Set-Offline $driver2 | Out-Null
    Set-Online $driver1 $pickupLat $pickupLng | Out-Null
    $created = New-Booking
    $bookingId = $created.Data.id
    Check 'driver has the offer before it expires' ((Get-Offers $driver1).Count -eq 1)
    Start-Sleep -Seconds ($OfferSeconds + 6)
    $final = Get-Booking $bookingId
    Check 'booking is CANCELLED after the offer expires' ($final.status -eq 'CANCELLED')
    Check 'cancel reason is NO_DRIVER_AVAILABLE' ($final.cancelReason -eq 'NO_DRIVER_AVAILABLE') "reason=$($final.cancelReason)"
    Check 'the expired offer is gone from the dashboard' ((Get-Offers $driver1).Count -eq 0)
}

Set-Offline $driver1 | Out-Null
Set-Offline $driver2 | Out-Null

Write-Host ''
Write-Host "$($script:passed + $script:failed) checks, $($script:failed) failed"
if ($script:failed -gt 0) { exit 1 }
