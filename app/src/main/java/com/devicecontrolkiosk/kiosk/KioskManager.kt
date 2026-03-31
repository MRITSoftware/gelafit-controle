package com.devicecontrolkiosk.kiosk

import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

object KioskManager {
    private const val FOREGROUND_LOOKBACK_MS = 15_000L

    fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, KioskDeviceAdminReceiver::class.java)
    }

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.isAdminActive(adminComponent(context))
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun syncLockTaskPackages(context: Context, controlledPackages: List<String>) {
        if (!isDeviceOwner(context)) {
            return
        }

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val packages = (listOf(context.packageName) + controlledPackages.distinct()).toTypedArray()
        try {
            dpm.setLockTaskPackages(adminComponent(context), packages)
        } catch (e: Exception) {
            Log.e("KioskManager", "Falha ao registrar lock task packages: ${e.message}")
        }
    }

    fun launchPackage(context: Context, packageName: String, enableLockTask: Boolean): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return try {
            if (enableLockTask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDeviceOwner(context)) {
                val options = ActivityOptions.makeBasic().apply {
                    setLockTaskEnabled(true)
                }
                context.startActivity(launchIntent, options.toBundle())
            } else {
                context.startActivity(launchIntent)
            }
            true
        } catch (securityException: SecurityException) {
            Log.w("KioskManager", "Launch com lock task negado, tentando fallback: ${securityException.message}")
            try {
                context.startActivity(launchIntent)
                true
            } catch (e: Exception) {
                Log.e("KioskManager", "Fallback de abertura falhou: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("KioskManager", "Erro ao abrir pacote $packageName: ${e.message}")
            false
        }
    }

    fun canMonitorForeground(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun getForegroundPackage(context: Context): String? {
        if (!canMonitorForeground(context)) {
            return null
        }

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - FOREGROUND_LOOKBACK_MS
        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                val candidate = event.packageName
                if (!candidate.isNullOrBlank()) {
                    lastPackage = candidate
                }
            }
        }

        return lastPackage
    }

    fun buildAddDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Necessario para kiosk reforcado, reboot remoto e inicializacao persistente."
            )
        }
    }

    fun rebootDevice(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isDeviceOwner(context)) {
            return false
        }

        return try {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            dpm.reboot(adminComponent(context))
            true
        } catch (e: Exception) {
            Log.e("KioskManager", "Falha ao reiniciar dispositivo: ${e.message}")
            false
        }
    }
}
