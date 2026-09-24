param(
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$RuleName = 'opencode-jarvis'
$Port = 4096
$Profiles = 'private,domain'

function Write-Log($msg) { Write-Host "[Jarvis-FW] $msg" }

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($id)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Test-RuleExists {
    $out = netsh advfirewall firewall show rule name="$RuleName" 2>$null
    return ($LASTEXITCODE -eq 0 -and $out -match 'Rule Name')
}

Write-Log "Checking Windows Firewall rule '$RuleName'..."
$exists = Test-RuleExists

if ($CheckOnly) {
    if ($exists) { Write-Log "Rule '$RuleName' EXISTS. Nothing to do." }
    else { Write-Log "Rule '$RuleName' does NOT exist. Run this script elevated to add it." }
    exit 0
}

if ($exists) {
    Write-Log "Rule '$RuleName' already exists. Nothing to do."
    exit 0
}

if (-not (Test-Admin)) {
    Write-Log 'Not elevated. Relaunching as Administrator...'
    $argLine = '-NoProfile -ExecutionPolicy Bypass -File "' + $MyInvocation.MyCommand.Path + '"'
    Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $argLine
    exit 0
}

Write-Log "Adding rule '$RuleName' for TCP $Port on profiles: $Profiles"
netsh advfirewall firewall add rule name="$RuleName" dir=in action=allow protocol=TCP localport=$Port profile=$Profiles

if ($LASTEXITCODE -ne 0) {
    Write-Log 'ERROR: failed to add the rule. Run from an elevated PowerShell window.'
    exit 1
}

Write-Log "Rule '$RuleName' added. Verify with: netsh advfirewall firewall show rule name=`"$RuleName`""
exit 0