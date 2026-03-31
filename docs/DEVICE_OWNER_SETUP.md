# Device Owner Setup

Este projeto usa o receiver `com.devicecontrolkiosk.kiosk.KioskDeviceAdminReceiver` para recursos de kiosk real.

## Pre-requisitos
- O aparelho deve estar resetado de fabrica, sem conta Google configurada.
- `adb` precisa estar instalado no computador.
- O app deve estar compilado em `app/build/outputs/apk/debug/app-debug.apk` ou voce pode passar outro caminho no script.

## Comando rapido
No PowerShell, a partir da raiz do projeto:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-device-owner.ps1
```

## O que o script faz
- instala ou atualiza o APK
- executa `dpm set-device-owner`
- libera `GET_USAGE_STATS` por `appops`
- coloca o app na whitelist de bateria
- abre a `MainActivity`

## Comandos equivalentes manuais
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dpm set-device-owner com.devicecontrolkiosk/com.devicecontrolkiosk.kiosk.KioskDeviceAdminReceiver
adb shell cmd appops set com.devicecontrolkiosk GET_USAGE_STATS allow
adb shell dumpsys deviceidle whitelist +com.devicecontrolkiosk
adb shell am start -n com.devicecontrolkiosk/.MainActivity
```

## Quando falhar
Se `dpm set-device-owner` retornar erro dizendo que o dispositivo ja foi provisionado, isso significa que:
- ja existe usuario/conta configurado no aparelho, ou
- o aparelho nao esta em estado limpo

Nesse caso, faca reset de fabrica e repita antes da configuracao inicial do Android.

## Resultado esperado
Depois disso, o app consegue:
- usar `lock task` real no app marcado como kiosk
- reiniciar o dispositivo por `DevicePolicyManager.reboot`
- subir junto no boot e restaurar o app kiosk automaticamente
