package com.scanner.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.scanner.app.homeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's name on the home stack: its repo's name, under Services → Phone apps. */
private const val APP = "doc-scanner"

/** A newer build than the one installed. */
data class Release(val versionCode: Int, val versionName: String, val notes: String)

/**
 * Updates from the home stack's dashboard, which keeps each release's APK (built and signed by
 * this repo's CI). Asking also tells the dashboard which version is installed, so its Services
 * page can say Update or Up to date. Auth is the same dashboard API token as the calendar upload.
 */
class UpdateClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** The newer release ready to install, or null (up to date, not ready yet, or unreachable). */
    suspend fun check(context: Context, baseUrl: String, token: String): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val (code, name) = installedVersion(context)
            val url = homeUrl(baseUrl, "/phone-apps/$APP/latest").toHttpUrl().newBuilder()
                .addQueryParameter("installed", code.toString())
                .addQueryParameter("installed_name", name)
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string() ?: return@use null)
                if (!json.optBoolean("update") || !json.optBoolean("ready")) return@use null
                val latest = json.getJSONObject("latest")
                Release(latest.getInt("version_code"), latest.getString("version_name"), latest.optString("notes"))
            }
        }.getOrNull()
    }

    /** Download the latest APK into the cache, or null if it didn't arrive whole. */
    suspend fun download(context: Context, baseUrl: String, token: String): File? = withContext(Dispatchers.IO) {
        val target = File(File(context.cacheDir, "updates").apply { mkdirs() }, "$APP.apk")
        val request = Request.Builder().url(homeUrl(baseUrl, "/phone-apps/$APP/download"))
            .header("Authorization", "Bearer $token").get().build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) return@use null
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
                target
            }
        }.getOrNull()
    }
}

/** This build's versionCode and versionName. */
fun installedVersion(context: Context): Pair<Long, String> {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    return code to (info.versionName ?: "")
}

/**
 * Hand the APK to Android's installer, which asks before replacing the app (and refuses a build not
 * signed with the same key). The first time, Android sends the user to allow installs from this app.
 */
fun install(context: Context, apk: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
