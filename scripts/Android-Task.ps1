[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Build', 'Install')][string]$Mode = 'Preflight',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [ValidatePattern('^emulator-[0-9]+$')][string]$Serial = 'emulator-5554',
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$Avd = 'Pixel_7',
    [string]$AvdHome = "$env:USERPROFILE\.android\avd",
    [ValidateRange(1, 900)][int]$TimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Android-Task.Core.ps1"
Invoke-AndroidTask @PSBoundParameters
