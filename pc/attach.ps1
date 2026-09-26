param(
    [string]$Session,
    [switch]$Continue,
    [string]$Dir,
    [switch]$TestOnly
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Dir) { $Dir = Split-Path -Parent $ScriptDir }
$EnvFile = Join-Path $ScriptDir '.env'
$BaseUrl = 'http://localhost:4096'
$Port = 4096

function Write-Log($msg) { Write-Host "[Jarvis] $msg" }

function Find-OpenCode {
    $candidates = @(
        (Get-Command 'opencode.cmd' -ErrorAction SilentlyContinue).Source,
        (Get-Command 'opencode.bat' -ErrorAction SilentlyContinue).Source,
        (Get-Command 'opencode' -ErrorAction SilentlyContinue).Source,
        (Join-Path $env:APPDATA 'npm\opencode.cmd'),
        'C:\Program Files\opencode\opencode.exe'
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    return $null
}

function Read-EnvFile {
    if (-not (Test-Path -LiteralPath $EnvFile)) { return $null }
    $ht = @{}
    Get-Content -LiteralPath $EnvFile | Where-Object { $_ -match '^\s*([^#=]+)=(.*)\s*$' } | ForEach-Object {
        $key = $matches[1].Trim()
        $val = $matches[2].Trim()
        if (-not $ht.ContainsKey($key)) { $ht[$key] = $val }
    }
    return $ht
}

function Get-LocalIpv4 {
    $ips = @()
    try {
        $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object { $_.IPAddress -notlike '169.254*' -and $_.IPAddress -ne '127.0.0.1' -and $_.PrefixOrigin -ne 'WellKnown' } |
            ForEach-Object { $_.IPAddress } | Sort-Object -Unique
    } catch {}
    if (-not $ips) {
        $ips = [System.Net.Dns]::GetHostAddresses($env:COMPUTERNAME) | ForEach-Object { $_.IPAddressToString } | Sort-Object -Unique
    }
    return @($ips)
}

function Test-Health {
    param($Uri, $User, $Password)
    try {
        $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${User}:${Password}"))
        $r = Invoke-WebRequest -Uri $Uri -Headers @{ Authorization = "Basic $b64" } -UseBasicParsing -TimeoutSec 5
        return ($r.StatusCode -eq 200 -and $r.Content -match 'healthy')
    } catch {
        return $false
    }
}

$opencode = Find-OpenCode
if (-not $opencode) {
    Write-Log 'ERROR: opencode not found. Install it first (npm install -g opencode-ai or https://opencode.ai/docs).'
    exit 1
}
Write-Log "opencode found at: $opencode"

$envFromFile = Read-EnvFile
if (-not $envFromFile -or -not $envFromFile['OPENCODE_SERVER_PASSWORD']) {
    Write-Log 'ERROR: pc\.env not found or missing OPENCODE_SERVER_PASSWORD.'
    Write-Log 'The shared server has not been initialized. Start it once with: .\pc\serve.ps1'
    exit 1
}

$user = if ($envFromFile['OPENCODE_SERVER_USERNAME']) { $envFromFile['OPENCODE_SERVER_USERNAME'] } else { 'opencode' }
$password = $envFromFile['OPENCODE_SERVER_PASSWORD']
Write-Log "Using credentials from pc\.env (user: $user)."

$healthUris = @("$BaseUrl/global/health", "http://127.0.0.1:$Port/global/health")
$lanCandidate = $envFromFile['OPENCODE_SERVER_HOST']
if (-not $lanCandidate) {
    $lanCandidate = @(Get-LocalIpv4) | Select-Object -First 1
}
if ($lanCandidate) { $healthUris += "http://$lanCandidate`:$Port/global/health" }

$healthy = $false
foreach ($uri in $healthUris) {
    if (Test-Health -Uri $uri -User $user -Password $password) {
        Write-Log "Server reachable at: $uri"
        $healthy = $true
        break
    }
}

if (-not $healthy) {
    Write-Log 'ERROR: jarvis server is not running (none of the health checks responded).'
    Write-Log 'Start it in another terminal with: .\pc\serve.ps1'
    exit 1
}

$AttachUrl = $uri -replace '/global/health$', ''
Write-Log "Attach URL: $AttachUrl"

$env:OPENCODE_SERVER_USERNAME = $user
$env:OPENCODE_SERVER_PASSWORD = $password

$argsList = @('attach', $AttachUrl, '--dir', "`"$Dir`"")
if ($Session) { $argsList += @('-s', $Session) }
if ($Continue) { $argsList += '-c' }

$display = "& `"$opencode`" " + ($argsList -join ' ')
Write-Log "About to run: $display"
Write-Log "Session env: OPENCODE_SERVER_USERNAME=$user OPENCODE_SERVER_PASSWORD=<set>"

if ($TestOnly) {
    Write-Log 'TestOnly: not launching the TUI.'
    exit 0
}

Write-Log 'Launching TUI (Ctrl+C twice to detach, keep the server running).'
& $opencode @argsList