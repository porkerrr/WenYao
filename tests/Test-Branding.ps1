$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -Raw (Join-Path $root "app/src/main/AndroidManifest.xml")
$strings = Get-Content -Raw (Join-Path $root "app/src/main/res/values/strings.xml")
$gradle = Get-Content -Raw (Join-Path $root "app/build.gradle.kts")

if ($manifest -notmatch 'android:icon="@mipmap/ic_launcher"') {
    throw "Launcher icon is not configured."
}
if ($manifest -notmatch 'android:roundIcon="@mipmap/ic_launcher_round"') {
    throw "Round launcher icon is not configured."
}
if ($manifest -notmatch 'android:label="@string/app_name"') {
    throw "Application label does not use app_name."
}
if ($strings -notmatch '<string name="app_name">问爻</string>') {
    throw "Application name is not 问爻."
}
if ($gradle -notmatch 'versionName = "1\.0\.0"') {
    throw "Release version is not 1.0.0."
}

$densitySizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

Add-Type -AssemblyName System.Drawing
foreach ($entry in $densitySizes.GetEnumerator()) {
    foreach ($name in @("ic_launcher.png", "ic_launcher_round.png")) {
        $path = Join-Path $root "app/src/main/res/$($entry.Key)/$name"
        if (-not (Test-Path $path)) {
            throw "Missing launcher asset: $path"
        }
        $image = [System.Drawing.Image]::FromFile($path)
        try {
            if ($image.Width -ne $entry.Value -or $image.Height -ne $entry.Value) {
                throw "Unexpected launcher size at ${path}: $($image.Width)x$($image.Height)"
            }
        } finally {
            $image.Dispose()
        }
    }
}

$foreground = [System.Drawing.Bitmap]::new((Join-Path $root 'app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png'))
try {
    if ($foreground.Width -ne 432 -or $foreground.Height -ne 432) { throw 'Adaptive foreground must be 108dp at xxxhdpi.' }
    if ($foreground.GetPixel(0, 0).A -ne 0 -or $foreground.GetPixel(216, 216).A -eq 0) { throw 'Adaptive icon must retain transparent padding and visible artwork.' }
} finally { $foreground.Dispose() }
if ((Get-Content -Raw (Join-Path $root 'app/src/main/res/values/colors.xml')) -notmatch '#F6F2EA') { throw 'Launcher background must match warm paper.' }
Write-Output "Branding validation passed."
