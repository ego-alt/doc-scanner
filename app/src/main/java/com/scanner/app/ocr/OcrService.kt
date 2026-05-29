package com.scanner.app.ocr

import android.graphics.Bitmap

/** Structured fields extracted from a receipt. Extend as the backend grows. */
data class ReceiptFields(
    val merchant: String? = null,
    val date: String? = null,
    val total: String? = null,
    val rawText: String = "",
    val extra: Map<String, String> = emptyMap()
)

sealed interface OcrResult {
    data class Success(val fields: ReceiptFields) : OcrResult
    data class Failure(val message: String) : OcrResult

    /** No OCR backend is wired up yet. */
    data object NotConfigured : OcrResult
}

/**
 * Extracts structured fields from a flattened receipt image.
 *
 * The on-device pipeline (capture -> detect -> flatten) produces a clean,
 * front-facing bitmap and hands it here. Implementations may run locally or
 * upload the image to a server.
 */
interface OcrService {
    suspend fun extract(bitmap: Bitmap): OcrResult
}
