# Launch the Swing GUI of the Steam balance-arbitrage calculator.
#
# FlatLaf (if present in lib\) is picked up reflectively by the application, so
# this script only has to put the JARs on the classpath.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $root 'out\classes'
$lib  = Join-Path $root 'lib'

if (-not (Test-Path -LiteralPath (Join-Path $out 'com\steamarbitrage\Main.class'))) {
    & (Join-Path $root 'build.ps1')
}

function Get-LibJars {
    if (-not (Test-Path -LiteralPath $lib)) { return @() }
    return @(Get-ChildItem -LiteralPath $lib -Filter '*.jar' -ErrorAction SilentlyContinue |
             Select-Object -ExpandProperty FullName)
}

# Fetch the optional UI dependency on first run (best effort, never fatal).
$jars = Get-LibJars
if ($jars.Count -eq 0) {
    & (Join-Path $root 'download-deps.ps1')
    $jars = Get-LibJars
}

$classpath = $out
if ($jars.Count -gt 0) {
    $classpath = "$out;" + ($jars -join ';')
    Write-Host ("Classpath: out\classes + {0} JAR(s)" -f $jars.Count)
} else {
    Write-Host 'Classpath: out\classes (no optional JAR - using system look and feel)'
}

$javaw = 'javaw'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    if (Test-Path -LiteralPath $candidate) { $javaw = $candidate }
}

# FlatLaf loads a small native helper for HiDPI window scaling.
# Without this flag JDK 24+ prints a restricted-method warning on startup.
$vmArgs = @('-Dfile.encoding=UTF-8', '--enable-native-access=ALL-UNNAMED')

Start-Process -FilePath $javaw `
              -ArgumentList ($vmArgs + @('-cp', $classpath, 'com.steamarbitrage.Main', 'gui')) |
    Out-Null

Write-Host 'GUI launched.'
