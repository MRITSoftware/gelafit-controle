package com.devicecontrolkiosk.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import com.devicecontrolkiosk.data.SupabaseApi
import android.util.Log
import android.content.SharedPreferences
import org.json.JSONObject
import android.app.ActivityManager
import android.os.PowerManager

class CommandService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pollingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob == null) {
            pollingJob = serviceScope.launch {
                val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", null)
                if (deviceId.isNullOrBlank()) {
                    Log.e("CommandService", "Device ID não encontrado. Serviço não pode iniciar polling.")
                    stopSelf()
                    return@launch
                }
                while (isActive) {
                    try {
                        val commands = SupabaseApi.pollCommands(deviceId)
                        for (cmd in commands) {
                            Log.i("CommandService", "Comando recebido: ${cmd.command}")
                            try {
                                val json = JSONObject(cmd.command)
                                val type = json.getString("type")
                                val payload = json.optJSONObject("payload")
                                when (type) {
                                    "restart_app" -> {
                                        val pkg = payload?.getString("package")
                                        if (pkg != null) {
                                            restartApp(pkg)
                                        }
                                    }
                                    "set_kiosk" -> {
                                        val pkg = payload?.getString("package")
                                        if (pkg != null) {
                                            setKioskMode(pkg)
                                        }
                                    }
                                    "restart_device" -> {
                                        restartDevice()
                                    }
                                    "set_apps" -> {
                                        val apps = payload?.optJSONArray("apps")
                                        if (apps != null) {
                                            val appList = (0 until apps.length()).map { apps.getString(it) }
                                            setControlledApps(appList)
                                        }
                                    }
                                    else -> Log.w("CommandService", "Tipo de comando desconhecido: $type")
                                }
                            } catch (e: Exception) {
                                Log.e("CommandService", "Erro ao processar comando: ${e.message}")
                            }
                            val marked = SupabaseApi.markCommandExecuted(cmd.id)
                            if (marked) {
                                Log.i("CommandService", "Comando ${cmd.id} marcado como executado.")
                            } else {
                                Log.e("CommandService", "Falha ao marcar comando ${cmd.id} como executado.")
                            }
                        }
                        // Reiniciar app: força parada e inicia app
                        private fun restartApp(packageName: String) {
                            Log.i("CommandService", "Reiniciando app: $packageName")
                            try {
                                // Força parada (requer permissão Device Owner)
                                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                                am.killBackgroundProcesses(packageName)
                                // Inicia app
                                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                } else {
                                    Log.e("CommandService", "Não foi possível obter intent para $packageName")
                                }
                            } catch (e: Exception) {
                                Log.e("CommandService", "Erro ao reiniciar app: ${e.message}")
                            }
                        }

                        // Ativar modo quiosque: inicia app em tela cheia (requer Device Owner para lock real)
                        private fun setKioskMode(packageName: String) {
                            Log.i("CommandService", "Ativando modo quiosque para: $packageName")
                            try {
                                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                    // TODO: Para lock real, usar APIs de lockTask (Device Owner)
                                } else {
                                    Log.e("CommandService", "Não foi possível obter intent para $packageName")
                                }
                            } catch (e: Exception) {
                                Log.e("CommandService", "Erro ao ativar modo quiosque: ${e.message}")
                            }
                        }

                        // Reiniciar dispositivo (requer permissão especial)
                        private fun restartDevice() {
                            Log.i("CommandService", "Reiniciando dispositivo (placeholder)")
                            // TODO: Para reboot real, precisa Device Owner: pm.reboot(null) via DevicePolicyManager
                        }

                        // Salvar apps controlados em SharedPreferences
                        private fun setControlledApps(apps: List<String>) {
                            Log.i("CommandService", "Definindo apps controlados: $apps")
                            val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
                            prefs.edit().putStringSet("controlled_apps", apps.toSet()).apply()
                        }
                    } catch (e: Exception) {
                        Log.e("CommandService", "Erro no polling: ${e.message}")
                    }
                    delay(10000) // Poll a cada 10 segundos
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
