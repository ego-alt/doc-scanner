package com.scanner.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Common written date formats, tried in order. Month-first is tried before day-first, so an
// ambiguous value like 05/06/2026 defaults to US (May 6); unambiguous ones (29/05) self-correct.
private val DATE_PATTERNS = listOf(
    "yyyy/MM/dd", "yyyy-MM-dd", "yyyy.MM.dd",
    "MM/dd/yyyy", "dd/MM/yyyy",
    "MM-dd-yyyy", "dd-MM-yyyy",
    "MMM d, yyyy", "MMMM d, yyyy",
    "d MMM yyyy", "d MMMM yyyy"
)

// Matches a clock time like 14:30, 2:30 PM, or 14:30:45 within the matched date/time text.
private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][.]?[Mm])?""")

/**
 * On-device OCR + date extraction with ML Kit: bundled (offline) text recognition plus entity
 * extraction, which downloads a small language model on first use. Nothing leaves the device.
 */
class OnDeviceOcrService : OcrService, Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    override suspend fun extract(bitmap: Bitmap): OcrResult {
        return try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
            if (text.isBlank()) return OcrResult.Success(ExtractedFields(rawText = ""))

            entityExtractor.downloadModelIfNeeded().await()
            // Pull the date/time as written on the document (avoids the time-zone off-by-one of the
            // entity timestamp). isoDate/isoTime normalize them to YYYY-MM-DD / HHMM for filenames.
            val dateTexts = entityExtractor.annotate(text).await()
                .filter { annotation -> annotation.entities.any { it.asDateTimeEntity() != null } }
                .map { it.annotatedText.trim() }
            val dateText = dateTexts.firstOrNull { toIsoDate(it) != null } ?: dateTexts.firstOrNull()

            OcrResult.Success(
                ExtractedFields(
                    date = dateText,
                    isoDate = dateText?.let { toIsoDate(it) },
                    isoTime = dateTexts.firstNotNullOfOrNull { toIsoTime(it) },
                    rawText = text
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OcrResult.Failure(e.localizedMessage ?: "Couldn't read the document.")
        }
    }

    /** Normalize a written date (one of [DATE_PATTERNS]) to YYYY-MM-DD, or null if unrecognized. */
    private fun toIsoDate(raw: String): String? {
        val utc = TimeZone.getTimeZone("UTC")
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
        for (pattern in DATE_PATTERNS) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = utc
            }
            val parsed = runCatching { parser.parse(raw) }.getOrNull()
            if (parsed != null) return iso.format(parsed)
        }
        return null
    }

    /** Extract a clock time from [raw] as HHMM (24-hour), or null if none is present. */
    private fun toIsoTime(raw: String): String? {
        val match = TIME_REGEX.find(raw) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (minute > 59) return null
        val ampm = match.groupValues[3].lowercase()
        when {
            ampm.startsWith("p") -> if (hour < 12) hour += 12
            ampm.startsWith("a") -> if (hour == 12) hour = 0
        }
        if (hour > 23) return null
        return String.format(Locale.US, "%02d%02d", hour, minute)
    }

    override fun close() {
        recognizer.close()
        entityExtractor.close()
    }
}
