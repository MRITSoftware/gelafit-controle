param(
    [string]$Adb = "adb",
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$packageName = "com.devicecontrolkiosk"
$adminComponent = "com.devicecontrolkiosk/com.devicecontrolkiosk.kiosk.KioskDeviceAdminReceiver"

function Invoke-Adb {
    param([string[]]$Args)
    Write-Host "> $Adb $($Args -join ' ')"
    & $Adb @Args
    if ($LASTEXITCODE -ne 0) {
        throw "Falha executando adb $($Args -join ' ')"
    }
}

Invoke-Adb @("start-server")
Invoke-Adb @("devices")

if (-not $SkipInstall) {
    if (-not (Test-Path $ApkPath)) {
        throw "APK nao encontrado em $ApkPath"
    }
    Invoke-Adb @("install", "-r", $ApkPath)
}

Invoke-Adb @("shell", "dpm", "set-device-owner", $adminComponent)
Invoke-Adb @("shell", "cmd", "appops", "set", $packageName, "GET_USAGE_STATS", "allow")
Invoke-Adb @("shell", "dumpsys", "deviceidle", "whitelist", "+$packageName")
Invoke-Adb @("shell", "am", "start", "-n", "$packageName/.MainActivity")

Write-Host "Provisionamento concluido."
Write-Host "Se o comando set-device-owner falhar, o aparelho provavelmente ja foi provisionado e precisa ser resetado de fabrica."
