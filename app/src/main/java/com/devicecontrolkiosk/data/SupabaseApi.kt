package com.devicecontrolkiosk.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

object SupabaseApi {
    private const val BASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co/rest/v1/"
    private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"
    const val KIOSK_MODE_TABLE = "device_kiosk_modes"
    private val jsonMediaType = "application/json".toMediaType()
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun registerDevice(deviceId: String, unitEmail: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedEmail = unitEmail.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return@withContext false
        }

        val json = """{"device_id":"$deviceId","unit_name":"$normalizedEmail"}"""
        val existingDevice = findDeviceByUnitEmail(normalizedEmail)
        val request = Request.Builder()
            .url(
                if (existingDevice == null) {
                    "${BASE_URL}devices"
                } else {
                    "${BASE_URL}devices?id=eq.${existingDevice.id}"
                }
            )
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .apply {
                if (existingDevice == null) {
                    post(json.toRequestBody(jsonMediaType))
                } else {
                    patch(json.toRequestBody(jsonMediaType))
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: IOException) {
            false
        }
    }

    suspend fun pollCommands(deviceId: String): List<DeviceCommand> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BASE_URL}device_commands?device_id=eq.$deviceId&executed=is.false&order=created_at.desc")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val adapter = moshi.adapter<List<DeviceCommand>>(
                    Types.newParameterizedType(List::class.java, DeviceCommand::class.java)
                )
                val payload = response.body?.string() ?: return@withContext emptyList()
                adapter.fromJson(payload) ?: emptyList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    suspend fun markCommandExecuted(commandId: String): Boolean = withContext(Dispatchers.IO) {
        val json = """{"executed":true,"executed_at":"${Instant.now()}"}"""
        val request = Request.Builder()
            .url("${BASE_URL}device_commands?id=eq.$commandId")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .patch(json.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: IOException) {
            false
        }
    }

    suspend fun fetchKioskPackage(deviceId: String, allowedPackages: List<String>): String? = withContext(Dispatchers.IO) {
        if (deviceId.isBlank() || allowedPackages.isEmpty()) {
            return@withContext null
        }

        val request = Request.Builder()
            .url("${BASE_URL}${KIOSK_MODE_TABLE}?select=package_name&device_id=eq.${encodeValue(deviceId)}&is_kiosk=eq.true&limit=1")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val payload = response.body?.string() ?: return@withContext null
                val adapter = moshi.adapter<List<DeviceKioskMode>>(
                    Types.newParameterizedType(List::class.java, DeviceKioskMode::class.java)
                )
                val mode = adapter.fromJson(payload)?.firstOrNull()
                mode?.packageName?.takeIf { allowedPackages.contains(it) }
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun syncKioskModes(deviceId: String, controlledPackages: List<String>, kioskPackage: String?): Boolean =
        withContext(Dispatchers.IO) {
            val normalizedPackages = controlledPackages.distinct().take(2)
            if (deviceId.isBlank() || normalizedPackages.isEmpty()) {
                return@withContext false
            }

            val normalizedKiosk = kioskPackage?.takeIf { normalizedPackages.contains(it) }
            val json = buildString {
                append("[")
                normalizedPackages.forEachIndexed { index, packageName ->
                    if (index > 0) append(",")
                    append(
                        """{"device_id":"$deviceId","package_name":"$packageName","is_kiosk":${packageName == normalizedKiosk},"updated_at":"${Instant.now()}"}"""
                    )
                }
                append("]")
            }

            val deleteRequest = Request.Builder()
                .url("${BASE_URL}${KIOSK_MODE_TABLE}?device_id=eq.${encodeValue(deviceId)}")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer $API_KEY")
                .delete()
                .build()

            val insertRequest = Request.Builder()
                .url("${BASE_URL}${KIOSK_MODE_TABLE}")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(deleteRequest).execute().use { deleteResponse ->
                    if (!deleteResponse.isSuccessful) {
                        return@withContext false
                    }
                }
                client.newCall(insertRequest).execute().use { insertResponse ->
                    insertResponse.isSuccessful
                }
            } catch (_: IOException) {
                false
            }
        }

    private fun findDeviceByUnitEmail(unitEmail: String): RegisteredDevice? {
        val encodedEmail = encodeValue(unitEmail)
        val request = Request.Builder()
            .url("${BASE_URL}devices?select=id,device_id,unit_name&unit_name=eq.$encodedEmail&limit=1")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }

                val payload = response.body?.string() ?: return null
                val adapter = moshi.adapter<List<RegisteredDevice>>(
                    Types.newParameterizedType(List::class.java, RegisteredDevice::class.java)
                )
                adapter.fromJson(payload)?.firstOrNull()
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun encodeValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }
}

data class DeviceCommand(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "command") val command: String,
    @Json(name = "executed") val executed: Boolean?,
    @Json(name = "executed_at") val executedAt: String?,
    @Json(name = "created_at") val createdAt: String?
)

data class RegisteredDevice(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String?,
    @Json(name = "unit_name") val unitName: String?
)

data class DeviceKioskMode(
    @Json(name = "device_id") val deviceId: String?,
    @Json(name = "package_name") val packageName: String?,
    @Json(name = "is_kiosk") val isKiosk: Boolean?
)
