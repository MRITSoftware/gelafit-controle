package com.devicecontrolkiosk.service

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.devicecontrolkiosk.data.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class CommandService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob == null) {
            pollingJob = serviceScope.launch {
                val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", null)
                if (deviceId.isNullOrBlank()) {
                    Log.e("CommandService", "Device ID nao encontrado. Servico nao pode iniciar polling.")
                    stopSelf()
                    return@launch
                }

                while (isActive) {
                    try {
                        val commands = SupabaseApi.pollCommands(deviceId)
                        for (cmd in commands) {
                            processCommand(cmd.command)
                            val marked = SupabaseApi.markCommandExecuted(cmd.id)
                            if (marked) {
                                Log.i("CommandService", "Comando ${cmd.id} marcado como executado.")
                            } else {
                                Log.e("CommandService", "Falha ao marcar comando ${cmd.id} como executado.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CommandService", "Erro no polling: ${e.message}")
                    }
                    delay(10_000)
                }
            }
        }
        return START_STICKY
    }

    private fun processCommand(commandPayload: String) {
        Log.i("CommandService", "Comando recebido: $commandPayload")
        try {
            val json = JSONObject(commandPayload)
            val type = json.getString("type")
            val payload = json.optJSONObject("payload")
            when (type) {
                "restart_app" -> payload?.optString("package")?.takeIf { it.isNotBlank() }?.let(::restartApp)
                "set_kiosk" -> payload?.optString("package")?.takeIf { it.isNotBlank() }?.let(::setKioskMode)
                "restart_device" -> restartDevice()
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
    }

    private fun restartApp(packageName: String) {
        Log.i("CommandService", "Reiniciando app: $packageName")
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Log.e("CommandService", "Nao foi possivel obter intent para $packageName")
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao reiniciar app: ${e.message}")
        }
    }

    private fun setKioskMode(packageName: String) {
        Log.i("CommandService", "Ativando modo quiosque para: $packageName")
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Log.e("CommandService", "Nao foi possivel obter intent para $packageName")
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao ativar modo quiosque: ${e.message}")
        }
    }

    private fun restartDevice() {
        Log.i("CommandService", "Reiniciando dispositivo (placeholder)")
    }

    private fun setControlledApps(apps: List<String>) {
        Log.i("CommandService", "Definindo apps controlados: $apps")
        val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
        prefs.edit().putStringSet("controlled_apps", apps.toSet()).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
