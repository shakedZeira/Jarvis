param(
    [Alias('Host')]
    [string]$HostName
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $ScriptDir '.env'
$OutPng = Join-Path $ScriptDir 'pair.png'
$Port = 4096

function Write-Log($msg) { Write-Host "[Jarvis] $msg" }

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

function Find-Python {
    $cmd = Get-Command 'python' -ErrorAction SilentlyContinue
    if ($cmd) { return ,@($cmd.Source, @()) }
    $cmd = Get-Command 'py' -ErrorAction SilentlyContinue
    if ($cmd) { return ,@($cmd.Source, @('-3')) }
    return $null
}

$envFromFile = Read-EnvFile
$userName = 'opencode'
$password = $null
if ($envFromFile) {
    if ($envFromFile['OPENCODE_SERVER_USERNAME']) { $userName = $envFromFile['OPENCODE_SERVER_USERNAME'] }
    if ($envFromFile['OPENCODE_SERVER_PASSWORD']) { $password = $envFromFile['OPENCODE_SERVER_PASSWORD'] }
}
if (-not $password) {
    Write-Log "ERROR: OPENCODE_SERVER_PASSWORD not found in $EnvFile. Run serve.ps1 first."
    exit 1
}

$hostAddr = $HostName
if (-not $hostAddr) {
    $ips = @(Get-LocalIpv4)
    if (-not $ips -or -not $ips[0]) {
        Write-Log 'ERROR: could not detect a LAN IPv4 address. Use -Host <ip|hostname>.'
        exit 1
    }
    $hostAddr = $ips[0]
}

$payload = "opencode://${userName}:${password}@${hostAddr}:${Port}"
$url = "http://${hostAddr}:${Port}"

$py = Find-Python
if (-not $py) {
    Write-Log 'ERROR: python not found (tried python, py -3).'
    exit 1
}
$pyExe = $py[0]
$pyArgs = $py[1]

$tmpPng = Join-Path $ScriptDir '_pair_tmp.png'
try {
    if (Test-Path -LiteralPath $tmpPng) { Remove-Item -LiteralPath $tmpPng -Force }

    $escaped = $payload -replace "'", "''"
    & $pyExe @pyArgs -c "import qrcode; img=qrcode.make(r'$escaped'); img.save(r'$tmpPng')"
    if ($LASTEXITCODE -ne 0) { throw "QR generation failed (exit $LASTEXITCODE). Is python's qrcode module installed?" }
    if (-not (Test-Path -LiteralPath $tmpPng)) { throw "QR image was not created." }

    Move-Item -LiteralPath $tmpPng -Destination $OutPng -Force

    Write-Log "QR code saved: $OutPng"
    Write-Log "Payload: $payload"
    Write-Log "URL: $url"
    Start-Process -FilePath $OutPng
} catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    exit 1
} finally {
    Remove-Variable -Name payload -ErrorAction SilentlyContinue
    Remove-Variable -Name password -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $tmpPng) { Remove-Item -LiteralPath $tmpPng -Force }
}
