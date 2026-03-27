package com.devicecontrolkiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devicecontrolkiosk.service.CommandService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, CommandService::class.java))
        }
    }
}
