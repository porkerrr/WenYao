$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../scripts/Android-Task.Core.ps1"
function Assert($Value, $Message) { if (!$Value) { throw $Message } }
Assert (Test-TargetDevice "emulator-5554 device product:sdk" 'emulator-5554') 'Device rejected'
Assert (!(Test-TargetDevice "emulator-5554 offline" 'emulator-5554')) 'Offline accepted'
Assert (!(Test-TargetDevice "emulator-5556 device" 'emulator-5554')) 'Wrong serial accepted'
Assert (Test-AppForeground '  mResumedActivity: ActivityRecord{abc u0 com.peterpork.liuyao.debug/com.peterpork.liuyao.MainActivity t1}') 'Foreground rejected'
Assert (Test-AppForeground '    topResumedActivity=ActivityRecord{abc u0 com.peterpork.liuyao.debug/com.peterpork.liuyao.MainActivity t1}') 'Equals foreground rejected'
Assert (!(Test-AppForeground '  Hist #0: ActivityRecord{abc com.peterpork.liuyao.debug/com.peterpork.liuyao.MainActivity t1}')) 'History accepted as foreground'
Assert (!(Test-AppForeground '  mResumedActivity: ActivityRecord{abc com.other/.MainActivity t1}')) 'Wrong app accepted'
function Test-Path { param($LiteralPath) return $true }
function Remove-Item { param($LiteralPath) $script:calls.Add('remove-apk') }
function Get-FileHash { param($LiteralPath, $Algorithm) return @{ Hash = 'TEST' } }
function Start-Sleep {}
function Invoke-Tool([string]$File, [string[]]$Arguments) {
    $cmd = $Arguments -join ' '
    $script:calls.Add($cmd)
    if ($cmd -eq '-version') { return 'openjdk version "17.0.20"' }
    if ($cmd -eq 'devices -l') { return 'emulator-5554 device' }
    if ($cmd -match 'emu avd name') { if ($script:scenario -eq 'wrong-avd') { return 'Other' }; return "Pixel_7`nOK" }
    if ($cmd -match 'getprop') { return '1' }
    if ($cmd -match '^test ') { if ($script:scenario -eq 'build-failure') { throw 'simulated build failure' }; return 'BUILD SUCCESSFUL' }
    if ($cmd -match 'install -r') { if ($script:scenario -eq 'install-failure') { return 'Failure [TEST]' }; return 'Success' }
    if ($cmd -match 'dumpsys') { return 'mResumedActivity: ActivityRecord{abc u0 com.peterpork.liuyao.debug/com.peterpork.liuyao.MainActivity t1}' }
    return ''
}
$before = $env:ANDROID_USER_HOME
foreach ($scenario in @('success', 'build-failure', 'install-failure', 'wrong-avd', 'preflight')) {
    $script:scenario = $scenario
    $script:calls = [Collections.Generic.List[string]]::new()
    $failed = $false
    try { Invoke-AndroidTask -Mode $(if ($scenario -eq 'preflight') { 'Preflight' } else { 'Install' }) }
    catch { $failed = $true }
    Assert ([string]$env:ANDROID_USER_HOME -eq [string]$before) 'Environment leaked'
    Assert ($failed -eq ($scenario -in @('build-failure', 'install-failure', 'wrong-avd'))) "Unexpected result: $scenario"
    $installed = @($calls | Where-Object { $_ -match 'install -r' }).Count -gt 0
    $launched = @($calls | Where-Object { $_ -match 'am start' }).Count -gt 0
    if ($scenario -in @('build-failure', 'wrong-avd', 'preflight')) { Assert (!$installed) "Unsafe install: $scenario" }
    if ($scenario -eq 'install-failure') { Assert (!$launched) 'Launched after failed install' }
    if ($scenario -eq 'success') {
        Assert ($installed -and $launched) 'Success path incomplete'
        Assert ($calls.IndexOf('remove-apk') -lt $calls.IndexOf('test :app:assembleDebug --no-daemon --console=plain -Pkotlin.compiler.execution.strategy=in-process')) 'APK removal order wrong'
    }
}
Write-Host 'Android workflow tests passed (device parsing, foreground, failure gates, environment restoration).'
