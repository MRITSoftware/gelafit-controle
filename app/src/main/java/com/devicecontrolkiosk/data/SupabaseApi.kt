    suspend fun markCommandExecuted(commandId: String): Boolean = withContext(Dispatchers.IO) {
        val json = """{"executed":true,"executed_at":"${java.time.Instant.now()}"}"""
        val body = RequestBody.create(MediaType.get("application/json"), json)
        val request = Request.Builder()
            .url(BASE_URL + "device_commands?id=eq.$commandId")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .patch(body)
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
        }
    }
package com.devicecontrolkiosk.data

import okhttp3.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object SupabaseApi {
    private const val BASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co/rest/v1/"
    private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun registerDevice(deviceId: String, unitName: String?): Boolean = withContext(Dispatchers.IO) {
        val json = """{"device_id":"$deviceId","unit_name":${unitName?.let { "\"$it\"" } ?: "null" }}"""
        val body = RequestBody.create(MediaType.get("application/json"), json)
        val request = Request.Builder()
            .url(BASE_URL + "devices")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
        }
    }

    suspend fun pollCommands(deviceId: String): List<DeviceCommand> = withContext(Dispatchers.IO) {
        val url = BASE_URL + "device_commands?device_id=eq.$deviceId&executed=is.false&order=created_at.desc"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .get()
            .build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val adapter = moshi.adapter<List<DeviceCommand>>(Types.newParameterizedType(List::class.java, DeviceCommand::class.java))
                response.body()?.string()?.let { adapter.fromJson(it) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }
}

// Data class para comandos
import com.squareup.moshi.Json

data class DeviceCommand(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "command") val command: String,
    @Json(name = "executed") val executed: Boolean?,
    @Json(name = "executed_at") val executedAt: String?,
    @Json(name = "created_at") val createdAt: String?
)
