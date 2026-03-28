package com.devicecontrolkiosk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.service.CommandService
import com.devicecontrolkiosk.ui.AppNav
import com.devicecontrolkiosk.ui.theme.DeviceControlKioskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DeviceConfigStore.isRegistered(this)) {
            ContextCompat.startForegroundService(this, Intent(this, CommandService::class.java))
        }
        setContent {
            DeviceControlKioskTheme {
                AppNav()
            }
        }
    }
}
