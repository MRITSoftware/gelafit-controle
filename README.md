# Device Control Kiosk

App Android profissional para controle remoto de dispositivos, modo quiosque e gerenciamento de apps via Supabase.

## Funcionalidades
- Registro do dispositivo no Supabase
- Serviço background persistente
- Polling e execução de comandos remotos (reiniciar apps, modo quiosque, reboot)
- Layout moderno (Material 3, Jetpack Compose)
- Telas de onboarding e status

## Comandos suportados (Supabase)
- `restart_app`: Reinicia o app especificado
- `set_kiosk`: Ativa modo quiosque para o app
- `restart_device`: Reinicia o dispositivo
- `set_apps`: Define apps controlados

## Estrutura do projeto
- `app/src/main/java/com/devicecontrolkiosk/` — Código principal
- `service/CommandService.kt` — Serviço background
- `data/SupabaseApi.kt` — Integração Supabase
- `ui/screens/` — Telas Compose

## Como usar
1. Compile e instale o app em um dispositivo Android (preferencialmente como Device Owner)
2. Registre o dispositivo na tela inicial
3. Gerencie comandos via Supabase

## Observações
- Para reboot e modo quiosque real, é necessário permissão de Device Owner e APIs específicas.
- Os placeholders de execução real devem ser implementados conforme a política de segurança do Android.
