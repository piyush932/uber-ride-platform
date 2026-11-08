param([string]$Gateway = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$collection = Join-Path $root 'postman/Uber-Ride-Platform-Demo.postman_collection.json'
if (!(Get-Command newman -ErrorAction SilentlyContinue)) { throw 'Install the runner first: npm install -g newman' }
& newman run $collection --env-var "baseUrl=$Gateway" --bail --timeout-request 15000 --reporters cli
exit $LASTEXITCODE
