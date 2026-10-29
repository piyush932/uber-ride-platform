# Opens one PowerShell window per service.
# Build first:  .\scripts\mvn.ps1 clean install -DskipTests
# Usage (from the repo root):  .\scripts\start-all.ps1
# Everything starts with the in-memory defaults. To use your local MySQL / Redis, start
# auth, location and booking by hand with the profiles shown in the README instead.
$repoRoot = Split-Path -Parent $PSScriptRoot
$services = @('eureka-server', 'auth-service', 'location-service', 'socket-service', 'booking-service')

foreach ($name in $services) {
    $jar = Join-Path $repoRoot ('backend\' + $name + '\target\' + $name + '.jar')
    if (-not (Test-Path $jar)) {
        Write-Host ('Missing ' + $jar) -ForegroundColor Red
        Write-Host 'Run  .\scripts\mvn.ps1 clean install -DskipTests  first and check it ends with BUILD SUCCESS.' -ForegroundColor Red
        exit 1
    }
}

foreach ($name in $services) {
    $jar = Join-Path $repoRoot ('backend\' + $name + '\target\' + $name + '.jar')
    Write-Host ('Starting ' + $name)
    Start-Process powershell -WorkingDirectory $repoRoot -ArgumentList '-NoExit', '-Command', ('java -jar "' + $jar + '"')
    # let Eureka come up before the others register with it
    Start-Sleep -Seconds 3
}

Write-Host ''
Write-Host 'All services are starting. Give them about 30 seconds, then open http://localhost:8761'
