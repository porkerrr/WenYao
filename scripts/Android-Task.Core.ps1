function Invoke-Tool([string]$File, [string[]]$Arguments) {
    $output = & $File @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $File $($Arguments -join ' ')" }
    return ($output -join "`n")
}

function Test-TargetDevice([string]$Devices, [string]$Serial) {
    return $Devices -match "(?m)^$([regex]::Escape($Serial))\s+device(?:\s|$)"
}

function Test-AppForeground([string]$Activities) {
    return $Activities -match '(?m)^\s*(?:mResumedActivity|topResumedActivity)[:=][^\r\n]*\scom\.peterpork\.liuyao\.debug/(?:com\.peterpork\.liuyao\.MainActivity|\.MainActivity)(?:\s|})'
}

function Wait-AndroidCondition([scriptblock]$Condition, [int]$Seconds, [string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 1000
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out: $Description"
}

function Invoke-AndroidTask {
    param(
        [string]$Mode = 'Preflight',
        [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
        [string]$Serial = 'emulator-5554',
        [string]$Avd = 'Pixel_7',
        [string]$AvdHome = "$env:USERPROFILE\.android\avd",
        [int]$TimeoutSeconds = 180
    )
    $root = Split-Path $PSScriptRoot -Parent
    $settings = @{
        GRADLE_USER_HOME = "$root\.gradle-user-home"
        ANDROID_USER_HOME = "$root\.android-user-home"
        ANDROID_HOME = $Sdk
        ANDROID_SDK_ROOT = $Sdk
        ANDROID_AVD_HOME = $AvdHome
        JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/nonexistent-liuyao-build-sockets'
        GRADLE_OPTS = '-Dkotlin.compiler.execution.strategy=in-process'
        KOTLIN_COMPILER_EXECUTION_STRATEGY = 'in-process'
    }
    $previous = @{}
    foreach ($key in $settings.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
    Push-Location $root
    try {
        $adb = Join-Path $Sdk 'platform-tools/adb.exe'
        $emulator = Join-Path $Sdk 'emulator/emulator.exe'
        foreach ($file in @($adb, $emulator, "$root/gradlew.bat", "$Sdk/platforms/android-35/android.jar")) {
            if (!(Test-Path -LiteralPath $file)) { throw "Required file missing: $file" }
        }
        $java = if ($env:JAVA_HOME) { "$env:JAVA_HOME/bin/java.exe" } else { 'java.exe' }
        $version = Invoke-Tool $java @('-version')
        if ($version -notmatch 'version "17\.') { throw 'JDK 17 is required. Check JAVA_HOME/PATH.' }
        $devices = Invoke-Tool $adb @('devices', '-l')
        Write-Host $devices
        $ready = Test-TargetDevice $devices $Serial
        if ($Mode -eq 'Preflight') {
            if (!$ready) { throw "Target $Serial is not ready; Install mode can start $Avd." }
        } elseif ($Mode -eq 'Install' -and !$ready) {
            if ($devices -match "(?m)^$([regex]::Escape($Serial))\s") {
                throw "Target $Serial exists but is offline/unauthorized. Recover it first."
            }
            $names = Invoke-Tool $emulator @('-list-avds')
            if ($Avd -notin ($names -split '\r?\n')) { throw "AVD not found: $Avd in $AvdHome" }
            $port = [int]($Serial -replace '^emulator-', '')
            if ($port -lt 5554 -or $port -gt 5682 -or $port % 2) { throw 'Emulator port must be even, 5554..5682.' }
            Start-Process -FilePath $emulator -ArgumentList @('-avd', $Avd, '-port', $port) -WindowStyle Hidden | Out-Null
            Wait-AndroidCondition { Test-TargetDevice (Invoke-Tool $adb @('devices', '-l')) $Serial } $TimeoutSeconds 'device connection'
            $ready = $true
        }
        if ($ready -and $Mode -ne 'Build') {
            $actual = Invoke-Tool $adb @('-s', $Serial, 'emu', 'avd', 'name')
            if ($Avd -notin ($actual -split '\r?\n')) { throw "Target $Serial is not AVD $Avd." }
            Wait-AndroidCondition { (Invoke-Tool $adb @('-s', $Serial, 'shell', 'getprop', 'sys.boot_completed')).Trim() -eq '1' } $TimeoutSeconds 'Android boot completion'
        }
        if ($Mode -eq 'Preflight') { Write-Host 'Preflight passed; no build or installation performed.'; return }
        $apk = "$root/app/build/outputs/apk/debug/app-debug.apk"
        # Remove only the known output so a successful no-op cannot install an old APK.
        if (Test-Path -LiteralPath $apk) { Remove-Item -LiteralPath $apk }
        Write-Host (Invoke-Tool "$root/gradlew.bat" @('test', ':app:assembleDebug', '--no-daemon', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process'))
        if (!(Test-Path -LiteralPath $apk)) { throw 'Build did not produce the Debug APK.' }
        Write-Host "Debug APK SHA256: $((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash)"
        if ($Mode -eq 'Build') { Write-Host 'Build passed; device execution was not verified.'; return }
        # Recheck identity immediately before modifying the target.
        $actual = Invoke-Tool $adb @('-s', $Serial, 'emu', 'avd', 'name')
        if ($Avd -notin ($actual -split '\r?\n')) { throw 'Target AVD changed during build.' }
        $installed = Invoke-Tool $adb @('-s', $Serial, 'install', '-r', $apk)
        if ($installed -notmatch '(?m)^Success\s*$') { throw 'Installation was not confirmed.' }
        $null = Invoke-Tool $adb @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', 'com.peterpork.liuyao.debug/com.peterpork.liuyao.MainActivity')
        Wait-AndroidCondition { Test-AppForeground (Invoke-Tool $adb @('-s', $Serial, 'shell', 'dumpsys', 'activity', 'activities')) } $TimeoutSeconds 'MainActivity foreground'
        Write-Host 'Install passed: MainActivity is resumed. Visual behavior still requires separate verification.'
    } finally {
        Pop-Location
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
}
