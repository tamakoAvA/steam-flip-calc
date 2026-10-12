# Launch the Swing GUI of the Steam balance-arbitrage calculator.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $root 'out\classes'

if (-not (Test-Path -LiteralPath (Join-Path $out 'com\steamarbitrage\Main.class'))) {
    & (Join-Path $root 'build.ps1')
}

$javaw = 'javaw'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    if (Test-Path -LiteralPath $candidate) { $javaw = $candidate }
}

Start-Process -FilePath $javaw `
              -ArgumentList @('-Dfile.encoding=UTF-8', '-cp', $out, 'com.steamarbitrage.Main', 'gui') |
    Out-Null

Write-Host 'GUI launched.'
