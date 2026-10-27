# Smoke test for auth-service. Start auth-service first (Eureka is optional).
# Usage (from the repo root):  .\scripts\smoke-test-auth.ps1
param([string]$Base = 'http://localhost:8081')

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

function Call([string]$Method, [string]$Path, $Body = $null, [string]$Token = '') {
    $params = @{ Method = $Method; Uri = ($Base + $Path); UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($Token) { $params.Headers = @{ Authorization = ('Bearer ' + $Token) } }
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
    Write-Host ('auth-service is not reachable at ' + $Base + '. Start it first.') -ForegroundColor Red
    exit 1
}
Check 'health returns 200' $health.Status 200
Check 'health status is UP' $health.Json.status 'UP'

$email = 'rider' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + '@example.com'
$password = 'secret123'

$signup = Call 'POST' '/api/v1/auth/signup' @{ name = 'Test Rider'; email = $email; password = $password }
Check 'signup returns 201' $signup.Status 201
Check 'signup echoes the email' $signup.Json.email $email
Check 'signup does not return a password' ($null -eq $signup.Json.password) $true

$dup = Call 'POST' '/api/v1/auth/signup' @{ name = 'Test Rider'; email = $email; password = $password }
Check 'duplicate signup returns 409' $dup.Status 409

$bad = Call 'POST' '/api/v1/auth/signup' @{ name = ''; email = 'not-an-email'; password = '123' }
Check 'invalid signup returns 400' $bad.Status 400

$login = Call 'POST' '/api/v1/auth/login' @{ email = $email; password = $password }
Check 'login returns 200' $login.Status 200
Check 'login returns a token' ([string]::IsNullOrEmpty($login.Json.token)) $false

$wrong = Call 'POST' '/api/v1/auth/login' @{ email = $email; password = 'definitely-wrong' }
Check 'wrong password returns 401' $wrong.Status 401

$ghost = Call 'POST' '/api/v1/auth/login' @{ email = 'ghost@example.com'; password = $password }
Check 'unknown email returns 401' $ghost.Status 401

$me = Call 'GET' '/api/v1/auth/me' $null $login.Json.token
Check 'me with token returns 200' $me.Status 200
Check 'me returns the logged in user' $me.Json.email $email

$noToken = Call 'GET' '/api/v1/auth/me'
Check 'me without token returns 401' $noToken.Status 401

$garbage = Call 'GET' '/api/v1/auth/me' $null 'not.a.real.token'
Check 'me with garbage token returns 401' $garbage.Status 401

Write-Host ''
Write-Host ($script:total.ToString() + ' checks, ' + $script:failed.ToString() + ' failed')
if ($script:failed -gt 0) { exit 1 }
