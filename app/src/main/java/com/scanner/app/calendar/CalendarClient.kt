package com.scanner.app.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface CalendarHealth {
    data object Up : CalendarHealth
    data object Down : CalendarHealth
}

sealed interface UploadResult {
    data object Success : UploadResult
    data class Failure(val message: String) : UploadResult
}

/**
 * Talks to the self-hosted calendar (served at `<base>/calendar/` behind the
 * dashboard nginx) for the optional "upload scan to calendar" feature.
 *
 * Auth is a dashboard API token sent as `Authorization: Bearer <token>`; nginx's
 * auth_request validates it and forwards the user to the calendar, which attaches
 * the file to that date's entry. The host is reached over its Tailscale `*.ts.net`
 * name (a publicly-trusted TLS cert), so no custom CA or trust config is needed here.
 */
class CalendarClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Probe `<base>/calendar/healthz` (open, no auth) to decide whether to offer upload. */
    suspend fun checkHealth(baseUrl: String): CalendarHealth = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val request = Request.Builder().url("${normalize(baseUrl)}/calendar/healthz").get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (ok) CalendarHealth.Up else CalendarHealth.Down
    }

    /** Upload [file] as an attachment onto [isoDate]'s calendar entry. */
    suspend fun upload(
        baseUrl: String,
        token: String,
        file: File,
        mimeType: String,
        fileName: String,
        isoDate: String
    ): UploadResult = withContext(Dispatchers.IO) {
        val parts = isoDate.split("-")
        if (parts.size != 3) return@withContext UploadResult.Failure("Invalid date.")
        val (year, month, day) = parts

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("year", year)
            .addFormDataPart("month", month)
            .addFormDataPart("day", day)
            .addFormDataPart("file", fileName, file.asRequestBody(mimeType.toMediaType()))
            .build()

        val request = Request.Builder()
            .url("${normalize(baseUrl)}/calendar/attachments")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> UploadResult.Success
                    resp.code == 401 -> UploadResult.Failure("Not authorized — check the token in Settings.")
                    resp.code == 413 -> UploadResult.Failure("Too large for the server (max 10 MB).")
                    else -> UploadResult.Failure("Upload failed (HTTP ${resp.code}).")
                }
            }
        }.getOrElse { e ->
            UploadResult.Failure(e.message ?: "Couldn't reach the calendar.")
        }
    }

    private fun normalize(baseUrl: String): String = baseUrl.trim().trimEnd('/')
}
