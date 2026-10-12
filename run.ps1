# Run the Steam balance-arbitrage calculator (CLI).
# Examples:
#   .\run.ps1                                        # interactive mode
#   .\run.ps1 --buy 70 --sell 100 --target 1000      # CS2, buyer-paid price
#   .\run.ps1 70 100 1000 --game tf2                 # positional shorthand
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Rest
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $root 'out\classes'

if (-not (Test-Path -LiteralPath (Join-Path $out 'com\steamarbitrage\Main.class'))) {
    & (Join-Path $root 'build.ps1')
}

# Make PowerShell decode the JVM's stdout as UTF-8 so Chinese text is not mangled.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-Dstdin.encoding=UTF-8' `
       -cp $out com.steamarbitrage.Main @Rest
exit $LASTEXITCODE
