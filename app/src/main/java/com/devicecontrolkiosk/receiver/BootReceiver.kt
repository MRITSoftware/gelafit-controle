package com.devicecontrolkiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.devicecontrolkiosk.service.CommandService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CommandService::class.java)
                        .putExtra(CommandService.EXTRA_TRIGGER_LAUNCH, true)
                )
            }
        }
    }
}
