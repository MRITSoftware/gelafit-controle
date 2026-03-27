package com.devicecontrolkiosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.devicecontrolkiosk.ui.AppNav
import com.devicecontrolkiosk.ui.theme.DeviceControlKioskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceControlKioskTheme {
                AppNav()
            }
        }
    }
}
