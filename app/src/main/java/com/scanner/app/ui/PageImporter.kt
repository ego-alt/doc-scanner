package com.scanner.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

/** Copies ML Kit's scan output (page JPEGs + PDF) into our shareable cache and decodes previews. */
object PageImporter {

    private const val IMAGES_DIR = "images"
    private const val PDFS_DIR = "pdfs"

    /**
     * Copy the scanner's page images (and PDF, if present) into FileProvider-exposed cache dirs
     * and return a [ScanResult], or null if no page could be imported.
     */
    fun importResult(context: Context, imageUris: List<Uri>, pdfUri: Uri?): ScanResult? {
        val stamp = System.currentTimeMillis()
        val pages = imageUris.mapIndexedNotNull { index, uri ->
            copyToCache(context, uri, IMAGES_DIR, "page_${stamp}_$index.jpg")
        }
        if (pages.isEmpty()) return null
        val pdf = pdfUri?.let { copyToCache(context, it, PDFS_DIR, "scan_$stamp.pdf") }
        return ScanResult(pages, pdf, stamp)
    }

    private fun copyToCache(context: Context, uri: Uri, subdir: String, name: String): File? {
        val dir = File(context.cacheDir, subdir).apply { mkdirs() }
        val file = File(dir, name)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrNull()
        return if (copied != null && file.exists() && file.length() > 0) file else null
    }

    /** Decode [file] downsampled so its longest edge is roughly [maxEdge] px (bounds memory). */
    fun decodeScaled(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
