# Runs Maven inside the backend folder.
# If Maven is not installed, a local copy is downloaded into .tools/ the first time.
# Usage (from the repo root):  .\scripts\mvn.ps1 clean install -DskipTests
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MvnArgs
)

$ErrorActionPreference = 'Stop'
$mavenVersion = '3.9.9'
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $repoRoot '.tools'
$mavenHome = Join-Path $toolsDir ('apache-maven-' + $mavenVersion)

$installed = Get-Command mvn -ErrorAction SilentlyContinue
if ($installed) {
    $mvn = $installed.Source
} else {
    $mvn = Join-Path $mavenHome 'bin\mvn.cmd'
    if (-not (Test-Path $mvn)) {
        New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
        $zip = Join-Path $toolsDir 'maven.zip'
        $url = 'https://archive.apache.org/dist/maven/maven-3/' + $mavenVersion + '/binaries/apache-maven-' + $mavenVersion + '-bin.zip'
        Write-Host ('Maven not found. Downloading ' + $url)
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
        Remove-Item $zip
    }
}

if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host 'Java was not found. Install a JDK (17 to 25) and try again.' -ForegroundColor Red
    exit 1
}

Push-Location (Join-Path $repoRoot 'backend')
try {
    & $mvn @MvnArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
