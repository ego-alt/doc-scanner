package com.scanner.app.ocr

import android.graphics.Bitmap

/**
 * Placeholder for the future server-side OCR / vision step.
 *
 * When the backend is ready, implement [extract] to upload [bitmap] to your
 * endpoint and map the response into [ExtractedFields]. A typical wiring:
 *
 *   1. Compress the bitmap to JPEG into a ByteArray.
 *   2. POST it to your endpoint (Retrofit/OkHttp or HttpURLConnection),
 *      e.g. as multipart/form-data.
 *   3. Parse the JSON response and return OcrResult.Success(ExtractedFields(...)).
 *
 * Until then this returns [OcrResult.NotConfigured] so callers can show a
 * "coming soon" state instead of crashing.
 */
class RemoteOcrService(
    private val endpoint: String? = null
) : OcrService {

    override suspend fun extract(bitmap: Bitmap): OcrResult {
        if (endpoint.isNullOrBlank()) return OcrResult.NotConfigured

        // TODO: upload `bitmap` to `endpoint` and parse the response.
        return OcrResult.NotConfigured
    }
}
