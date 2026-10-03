param([Parameter(Mandatory = $true)][string]$Source)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Source))
try {
    if ($image.Width -ne $image.Height) { throw 'Launcher artwork must be square.' }
    function Write-Icon([string]$RelativePath, [int]$Size, [int]$ArtworkSize, [bool]$Round) {
        $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            if ($Round) {
                $clip = [System.Drawing.Drawing2D.GraphicsPath]::new()
                try { $clip.AddEllipse(0, 0, $Size, $Size); $graphics.SetClip($clip) }
                finally { $clip.Dispose() }
            }
            $offset = [int](($Size - $ArtworkSize) / 2)
            $graphics.DrawImage($image, $offset, $offset, $ArtworkSize, $ArtworkSize)
            $bitmap.Save((Join-Path $root $RelativePath), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $graphics.Dispose(); $bitmap.Dispose() }
    }
    foreach ($density in @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }.GetEnumerator()) {
        Write-Icon "app/src/main/res/mipmap-$($density.Key)/ic_launcher.png" $density.Value $density.Value $false
        Write-Icon "app/src/main/res/mipmap-$($density.Key)/ic_launcher_round.png" $density.Value $density.Value $true
    }
    # 108dp foreground canvas; keep supplied artwork within the central 72dp area.
    Write-Icon 'app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png' 432 288 $false
} finally { $image.Dispose() }
