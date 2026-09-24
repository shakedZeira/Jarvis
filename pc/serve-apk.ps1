param(
    [int]$Port = 8000,
    [switch]$RunHidden
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '..\android\app\build\outputs\apk\debug'))
$RuleName = 'opencode-jarvis-apk'
$PidFile = Join-Path $ScriptDir 'serve-apk.pid'

try {
    Write-Host "[Jarvis-APK] root: $Root"

    try {
        $out = netsh advfirewall firewall show rule name="$RuleName" 2>$null | Out-String
        $exists = $out -match 'Rule Name'
    } catch { $exists = $false }
    if (-not $exists) {
        netsh advfirewall firewall add rule name="$RuleName" dir=in action=allow protocol=TCP localport=$Port profile=any | Out-Null
        Write-Host "[Jarvis-APK] firewall rule added (all profiles)."
    }

    netsh http add urlacl url="http://+:$Port/" user=Everyone 2>$null | Out-Null

    net session > $null 2>&1
    $isAdmin = ($LASTEXITCODE -eq 0 -or -not $?)

    if ($RunHidden) {
        $listener = New-Object System.Net.HttpListener
        $listener.Prefixes.Add("http://+:$Port/")
        $listener.Start()
        $PID | Set-Content -LiteralPath $PidFile
        Write-Host "[Jarvis-APK] hidden server PID=$PID serving $Root" -ForegroundColor Green
        while ($listener.IsListening) {
            try {
                $ctx = $listener.GetContext()
            } catch {
                break
            }
            $requested = $ctx.Request.Url.AbsolutePath.TrimStart('/')
            $file = Join-Path $Root $requested
            if ((Test-Path -LiteralPath $file) -and -not (Get-Item -LiteralPath $file).PSIsContainer) {
                $bytes = [IO.File]::ReadAllBytes($file)
                $ctx.Response.StatusCode = 200
                $ctx.Response.ContentType = 'application/vnd.android.package-archive'
                $ctx.Response.AddHeader('Content-Disposition', 'attachment; filename="app-debug.apk"')
                $ctx.Response.ContentLength64 = $bytes.Length
                $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                Write-Host "[Jarvis-APK] sent $requested ($($bytes.Length) bytes)"
            } else {
                $html = '<html><body style="font-family:sans-serif;padding:2em"><h3>Jarvis APK</h3><a href="/app-debug.apk">Download app-debug.apk</a></body></html>'
                $b = [Text.Encoding]::UTF8.GetBytes($html)
                $ctx.Response.StatusCode = 404
                $ctx.Response.ContentType = 'text/html; charset=utf-8'
                $ctx.Response.ContentLength64 = $b.Length
                $ctx.Response.OutputStream.Write($b, 0, $b.Length)
            }
            $ctx.Response.OutputStream.Close()
        }
        exit 0
    }

    Write-Host "[Jarvis-APK] RunHidden not set - nothing to do (launch with -RunHidden)."
} catch {
    Write-Host "[Jarvis-APK] ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Press Enter to close..."
    Read-Host | Out-Null
    exit 1
}