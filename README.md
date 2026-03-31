# Device Control Kiosk

App Android profissional para controle remoto de dispositivos, modo quiosque e gerenciamento de apps via Supabase.

## Funcionalidades
- Registro do dispositivo no Supabase
- Servico background persistente
- Polling e execucao de comandos remotos
- Reinicio individual dos 2 apps controlados
- Watchdog de kiosk para trazer o app de volta ao foreground
- Inicializacao automatica no boot e apos atualizacao do app
- Suporte a Device Owner para lock task real e reboot remoto

## Comandos suportados (Supabase)
- `restart_app`: Reinicia o app especificado
- `restart_first_app`: Reinicia o primeiro app escolhido
- `restart_second_app`: Reinicia o segundo app escolhido
- `restart_controlled_apps`: Reinicia os dois apps controlados
- `set_kiosk`: Ativa modo quiosque para o app escolhido
- `restart_device`: Reinicia o dispositivo quando o app estiver como Device Owner
- `set_apps`: Define apps controlados e qual deles e o kiosk

## Estrutura do projeto
- `app/src/main/java/com/devicecontrolkiosk/` - Codigo principal
- `app/src/main/java/com/devicecontrolkiosk/service/CommandService.kt` - Servico foreground e watchdog de kiosk
- `app/src/main/java/com/devicecontrolkiosk/kiosk/KioskManager.kt` - Integracao com DevicePolicyManager, lock task e usage access
- `app/src/main/java/com/devicecontrolkiosk/data/SupabaseApi.kt` - Integracao Supabase
- `app/src/main/java/com/devicecontrolkiosk/ui/screens/` - Telas Compose
- `supabase/control_panel_schema.sql` - Schema atual usado pelo app
- `docs/DEVICE_OWNER_SETUP.md` - Passo a passo para provisionar o aparelho como Device Owner
- `scripts/setup-device-owner.ps1` - Script PowerShell com os comandos adb prontos

## Como usar
1. Compile e instale o app em um dispositivo Android.
2. Siga [docs/DEVICE_OWNER_SETUP.md](docs/DEVICE_OWNER_SETUP.md) para provisionar como Device Owner se quiser kiosk real.
3. Registre o dispositivo na tela inicial.
4. Escolha 2 apps e marque qual deles sera o kiosk.
5. Salve a configuracao para iniciar os apps e ativar o monitoramento.

## Observacoes
- Sem Device Owner, o app ainda tenta restaurar o kiosk se ele for minimizado, mas o lock task real depende dessa configuracao.
- Sem Usage Access, o watchdog nao consegue detectar qual app esta no foreground.
