package com.devicecontrolkiosk.data

import android.content.Context

object DeviceConfigStore {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_UNIT_EMAIL = "unit_email"
    private const val KEY_CONTROLLED_APPS = "controlled_apps"
    private const val KEY_KIOSK_PACKAGE = "kiosk_package"

    data class AppSelection(
        val deviceId: String?,
        val unitEmail: String?,
        val controlledPackages: List<String>,
        val kioskPackage: String?
    )

    fun getConfig(context: Context): AppSelection {
        val prefs = prefs(context)
        val controlled = prefs.getStringSet(KEY_CONTROLLED_APPS, emptySet()).orEmpty().toList().sorted()
        return AppSelection(
            deviceId = prefs.getString(KEY_DEVICE_ID, null),
            unitEmail = prefs.getString(KEY_UNIT_EMAIL, null),
            controlledPackages = controlled,
            kioskPackage = prefs.getString(KEY_KIOSK_PACKAGE, null)
        )
    }

    fun saveRegistration(context: Context, deviceId: String, unitEmail: String) {
        prefs(context).edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_UNIT_EMAIL, unitEmail)
            .apply()
    }

    fun saveAppSelection(context: Context, controlledPackages: List<String>, kioskPackage: String?) {
        val normalizedPackages = controlledPackages.distinct().take(2)
        val normalizedKiosk = kioskPackage?.takeIf { normalizedPackages.contains(it) }
        prefs(context).edit()
            .putStringSet(KEY_CONTROLLED_APPS, normalizedPackages.toSet())
            .putString(KEY_KIOSK_PACKAGE, normalizedKiosk)
            .apply()
    }

    fun isRegistered(context: Context): Boolean {
        return !getConfig(context).deviceId.isNullOrBlank()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
