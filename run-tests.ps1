# Run the zero-dependency self test suites. Exit code 0 = all assertions passed.
#   1) SelfTest     - core algorithm, fee formula, CLI argument parsing, report rendering
#   2) GuiSelfCheck - Swing combo-box wiring and GUI/CLI output parity (skipped if headless)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $root 'out\classes'

& (Join-Path $root 'build.ps1')

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$javaArgs = @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-cp', $out)

& java @javaArgs com.steamarbitrage.test.SelfTest
$coreExit = $LASTEXITCODE

Write-Host ''
& java @javaArgs com.steamarbitrage.test.GuiSelfCheck
$guiExit = $LASTEXITCODE

if ($coreExit -ne 0 -or $guiExit -ne 0) {
    Write-Host ''
    Write-Host ("FAILED (core={0}, gui={1})" -f $coreExit, $guiExit)
    exit 1
}

Write-Host ''
Write-Host 'ALL TESTS PASSED'
exit 0
