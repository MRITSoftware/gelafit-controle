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
import java.time.Instant

object SupabaseApi {
    private const val BASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co/rest/v1/"
    private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"
    private val jsonMediaType = "application/json".toMediaType()
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun registerDevice(deviceId: String, unitName: String?): Boolean = withContext(Dispatchers.IO) {
        val json = """{"device_id":"$deviceId","unit_name":${unitName?.let { "\"$it\"" } ?: "null"}}"""
        val request = Request.Builder()
            .url("${BASE_URL}devices")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody(jsonMediaType))
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
}

data class DeviceCommand(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "command") val command: String,
    @Json(name = "executed") val executed: Boolean?,
    @Json(name = "executed_at") val executedAt: String?,
    @Json(name = "created_at") val createdAt: String?
)
