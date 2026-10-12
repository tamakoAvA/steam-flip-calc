# Build the Steam balance-arbitrage calculator.
# No Maven / Gradle required - plain javac, zero third-party dependencies.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src  = Join-Path $root 'src\main\java'
$out  = Join-Path $root 'out\classes'

if (-not (Test-Path -LiteralPath $src)) { throw "Source directory not found: $src" }

if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null

$files = @(Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' |
           Select-Object -ExpandProperty FullName)
if ($files.Count -eq 0) { throw "No .java files found under $src" }

& javac --release 17 -encoding UTF-8 -d $out @files
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host ("Build OK: {0} source file(s) -> {1}" -f $files.Count, $out)
