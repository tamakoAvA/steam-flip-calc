# Package the calculator into Windows executables using the JDK's jpackage.
#
#   .\pack.ps1                  portable folder + installer
#   .\pack.ps1 -AppImageOnly    portable folder only (no WiX required)
#   .\pack.ps1 -Version 1.2.0   override the version string
#
# Output (under out\dist\):
#   steam-flip-calc\steam-flip-calc.exe     portable app (ship the whole folder)
#   steam-flip-calc-<version>.exe           installer
#
# The installer step needs WiX 3 (candle.exe / light.exe). If it is missing the
# script just warns and still produces the portable folder.
param(
    [string]$Version = '1.0.0',
    [switch]$AppImageOnly
)

$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$out     = Join-Path $root 'out'
$dist    = Join-Path $out 'dist'
$stage   = Join-Path $out 'package'
$lib     = Join-Path $root 'lib'
$icon    = Join-Path $root 'assets\app.ico'
$appName = 'steam-flip-calc'

Write-Host '--- 1/4  compile ---'
& (Join-Path $root 'build.ps1')

Write-Host '--- 2/4  collect dependencies ---'
function Get-LibJars {
    if (-not (Test-Path -LiteralPath $lib)) { return @() }
    return @(Get-ChildItem -LiteralPath $lib -Filter '*.jar' -ErrorAction SilentlyContinue)
}

$jars = Get-LibJars
if ($jars.Count -eq 0) {
    & (Join-Path $root 'download-deps.ps1')
    $jars = Get-LibJars
}

Write-Host '--- 3/4  stage the application jar ---'
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null

# The launcher only puts the main jar on the classpath, so the optional FlatLaf
# jar has to be referenced from the manifest's Class-Path.
$manifestLines = @('Manifest-Version: 1.0', 'Main-Class: com.steamarbitrage.Main')
$jarNames = @($jars | ForEach-Object { $_.Name })
if ($jarNames.Count -gt 0) {
    $manifestLines += "Class-Path: $($jarNames -join ' ')"
}
$manifestPath = Join-Path $out 'manifest.txt'
$manifestText = ($manifestLines -join "`r`n") + "`r`n`r`n"
[System.IO.File]::WriteAllText($manifestPath, $manifestText,
    (New-Object System.Text.UTF8Encoding($false)))

& jar --create --file (Join-Path $stage "$appName.jar") --manifest $manifestPath `
       -C (Join-Path $out 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'jar packaging failed' }

foreach ($jar in $jars) {
    Copy-Item -LiteralPath $jar.FullName -Destination $stage -Force
}

Write-Host '--- 4/4  jpackage ---'
if (Test-Path -LiteralPath $dist) { Remove-Item -LiteralPath $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist -Force | Out-Null

$baseArgs = @(
    '--name', $appName,
    '--input', $stage,
    '--main-jar', "$appName.jar",
    '--main-class', 'com.steamarbitrage.Main',
    '--app-version', $Version,
    '--dest', $dist,
    '--vendor', 'tamakoAvA',
    '--description', 'Steam balance arbitrage calculator (CS2 / TF2)',
    # Trim the bundled runtime: without this jpackage ships every JDK module
    # (~123 MB). jlink pulls in the transitive dependencies of these.
    '--add-modules', 'java.desktop,java.logging,jdk.unsupported',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '--enable-native-access=ALL-UNNAMED'
)
if (Test-Path -LiteralPath $icon) {
    $baseArgs += @('--icon', $icon)
} else {
    Write-Warning "icon not found: $icon"
}

& jpackage --type app-image @baseArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed' }

$portableExe = Join-Path $dist "$appName\$appName.exe"
$portableSize = [math]::Round(
    (Get-ChildItem -LiteralPath (Join-Path $dist $appName) -Recurse -File |
     Measure-Object -Property Length -Sum).Sum / 1MB, 1)
Write-Host "portable folder : out\dist\$appName\  ($portableSize MB)"
Write-Host "portable exe    : out\dist\$appName\$appName.exe"

if ($AppImageOnly) {
    Write-Host 'FINISHED (portable only)'
    exit 0
}

$wix = Join-Path $out 'tools\wix'
if (Test-Path -LiteralPath (Join-Path $wix 'candle.exe')) {
    $env:PATH = "$wix;$env:PATH"

    & jpackage --type exe @baseArgs --win-dir-chooser --win-menu --win-shortcut --win-per-user-install
    if ($LASTEXITCODE -ne 0) { throw 'jpackage installer failed' }

    $installer = Join-Path $dist "$appName-$Version.exe"
    $installerMB = [math]::Round((Get-Item -LiteralPath $installer).Length / 1MB, 1)
    Write-Host "installer       : out\dist\$appName-$Version.exe  ($installerMB MB)"
} else {
    Write-Warning "WiX not found at $wix - skipped the installer build."
    Write-Warning 'Unpack wix314-binaries.zip there (or use -AppImageOnly on purpose).'
}

Write-Host 'FINISHED'
