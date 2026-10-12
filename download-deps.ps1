# Download optional runtime dependencies into lib\.
#
# FlatLaf is loaded reflectively by the GUI, so the project still COMPILES with
# nothing but a JDK. This script only improves how the window looks at runtime.
#
# The proxy is auto-detected (HTTPS_PROXY / HTTP_PROXY env, then the Windows
# Internet Settings registry), so it works behind a local proxy without config.
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$lib     = Join-Path $root 'lib'
$version = '3.7.2'
$jarName = "flatlaf-$version.jar"
$jarPath = Join-Path $lib $jarName
$url     = "https://repo1.maven.org/maven2/com/formdev/flatlaf/$version/$jarName"

if (-not (Test-Path -LiteralPath $lib)) {
    New-Item -ItemType Directory -Path $lib -Force | Out-Null
}

function Test-ValidJar([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    if ((Get-Item -LiteralPath $Path).Length -lt 100KB) { return $false }
    # A JAR is a ZIP: it must start with the local file header signature "PK".
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $head = New-Object byte[] 2
        $null = $stream.Read($head, 0, 2)
        return ($head[0] -eq 0x50 -and $head[1] -eq 0x4B)
    } finally {
        $stream.Dispose()
    }
}

if (Test-ValidJar $jarPath) {
    Write-Host "FlatLaf $version already present: lib\$jarName"
    exit 0
}

# --- resolve proxy -----------------------------------------------------------
$proxy = $null
if ($env:HTTPS_PROXY) {
    $proxy = $env:HTTPS_PROXY
} elseif ($env:HTTP_PROXY) {
    $proxy = $env:HTTP_PROXY
} else {
    try {
        $inet = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction Stop
        if ($inet.ProxyEnable -eq 1 -and $inet.ProxyServer) {
            $proxy = $inet.ProxyServer
            if ($proxy -notmatch '^[a-zA-Z]+://') { $proxy = "http://$proxy" }
        }
    } catch {
        # registry key unavailable - fall through to a direct connection
    }
}

Write-Host "Downloading FlatLaf $version ..."
if ($proxy) { Write-Host "  proxy: $proxy" }

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $params = @{ Uri = $url; OutFile = $jarPath; UseBasicParsing = $true; TimeoutSec = 180 }
    if ($proxy) { $params['Proxy'] = $proxy }
    Invoke-WebRequest @params
} catch {
    Write-Warning "Download failed: $($_.Exception.Message)"
    if (Test-Path -LiteralPath $jarPath) { Remove-Item -LiteralPath $jarPath -Force -ErrorAction SilentlyContinue }
    Write-Warning 'The GUI still runs, but falls back to the system look and feel.'
    exit 1
}

if (Test-ValidJar $jarPath) {
    $kb = [math]::Round((Get-Item -LiteralPath $jarPath).Length / 1KB, 1)
    Write-Host "OK: lib\$jarName ($kb KB)"
} else {
    Write-Warning 'Downloaded file is not a valid JAR.'
    Remove-Item -LiteralPath $jarPath -Force -ErrorAction SilentlyContinue
    exit 1
}
