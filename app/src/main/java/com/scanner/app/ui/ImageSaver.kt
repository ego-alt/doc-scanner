package com.scanner.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares a cached file (image or PDF) via the system share sheet, under a chosen display name. */
object ImageSaver {

    fun shareFile(context: Context, file: File, mimeType: String, displayName: String) {
        // Copy into a FileProvider-exposed dir under the desired name so the recipient sees it.
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val shared = File(dir, displayName)
        file.copyTo(shared, overwrite = true)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shared
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
