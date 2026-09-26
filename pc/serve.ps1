param(
    [switch]$TestOnly
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $ScriptDir '.env'
$OpenCodeUser = 'opencode'

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
        $matches[1].Trim() | Out-Null
        $key = $matches[1].Trim()
        $val = $matches[2].Trim()
        if (-not $ht.ContainsKey($key)) { $ht[$key] = $val }
    }
    return $ht
}

function Write-EnvFile($env) {
    $content = @()
    $content += "OPENCODE_SERVER_USERNAME=$OpenCodeUser"
    $content += "OPENCODE_SERVER_PASSWORD=$($env.Password)"
    Set-Content -LiteralPath $EnvFile -Value $content -Encoding Ascii
    try { icacls "$EnvFile" /inheritance:r /grant:r "$($env:USERNAME):R" | Out-Null } catch {}
}

function New-RandomPassword {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $bytes = New-Object byte[] 24
    ([System.Security.Cryptography.RandomNumberGenerator]::Create()).GetBytes($bytes)
    $pw = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    return $pw
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

$opencode = Find-OpenCode
if (-not $opencode) {
    Write-Log 'ERROR: opencode not found. Install it first (npm install -g opencode-ai or https://opencode.ai/docs).'
    exit 1
}
Write-Log "opencode found at: $opencode"

$envFromFile = Read-EnvFile
if ($envFromFile -and $envFromFile['OPENCODE_SERVER_PASSWORD']) {
    $password = $envFromFile['OPENCODE_SERVER_PASSWORD']
    Write-Log 'Using existing password from pc\.env.'
}
else {
    $password = New-RandomPassword
    Write-Log 'No pc\.env password found. Generating a new one.'
    Write-EnvFile @{ Password = $password }
    Write-Log "Password stored in: $EnvFile"
}

$env:OPENCODE_SERVER_USERNAME = $OpenCodeUser
$env:OPENCODE_SERVER_PASSWORD = $password

$ips = @(Get-LocalIpv4)
Write-Log "OPENCODE_SERVER_USERNAME: $OpenCodeUser"
Write-Log "OPENCODE_SERVER_PASSWORD: $password"
Write-Log 'Local IPv4 address(es):'
foreach ($ip in $ips) { Write-Log "   $ip" }
Write-Log "Expected health-check URL (LAN): http://$($ips[0]):4096/global/health"
Write-Log 'mDNS (if supported): http://opencode.local:4096/global/health'
Write-Log "Health check from phone: Invoke-WebRequest -Uri 'http://$($ips[0]):4096/global/health' -Credential ..."

if ($TestOnly) {
    Write-Log 'TestOnly: not starting server.'
    exit 0
}

Write-Log "Starting server: opencode serve --hostname $($ips[0]) --port 4096 --mdns"
& $opencode serve --hostname $ips[0] --port 4096 --mdns