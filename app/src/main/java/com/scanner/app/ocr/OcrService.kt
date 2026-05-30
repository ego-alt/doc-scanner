package com.scanner.app.ocr

import android.graphics.Bitmap

/** Structured fields extracted from a scanned document. Extend as the backend grows. */
data class ExtractedFields(
    val merchant: String? = null,
    val date: String? = null,
    val isoDate: String? = null,
    val isoTime: String? = null,
    val total: String? = null,
    val rawText: String = "",
    val extra: Map<String, String> = emptyMap()
)

sealed interface OcrResult {
    data class Success(val fields: ExtractedFields) : OcrResult
    data class Failure(val message: String) : OcrResult

    /** No OCR backend is wired up yet. */
    data object NotConfigured : OcrResult
}

/**
 * Extracts structured fields from a flattened document image.
 *
 * The on-device pipeline (capture -> detect -> flatten) produces a clean,
 * front-facing bitmap and hands it here. Implementations may run locally or
 * upload the image to a server.
 */
interface OcrService {
    suspend fun extract(bitmap: Bitmap): OcrResult
}
