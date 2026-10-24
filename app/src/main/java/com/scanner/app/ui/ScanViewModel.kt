package com.scanner.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.app.calendar.CalendarClient
import com.scanner.app.calendar.CalendarHealth
import com.scanner.app.calendar.UploadResult
import com.scanner.app.ocr.OcrResult
import com.scanner.app.ocr.OnDeviceOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val OCR_MAX = 2000
private const val PREFS_NAME = "scanner_settings"
private const val KEY_EXTRACT_DATES = "extract_dates"
private const val KEY_CAL_ENABLED = "calendar_enabled"
private const val KEY_CAL_URL = "calendar_base_url"
private const val KEY_CAL_TOKEN = "calendar_token"
private const val DEFAULT_CAL_URL = ""

/** Progress of an upload to the calendar. */
sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data object Done : UploadState
    data class Error(val message: String) : UploadState
}

/** Snapshot of calendar-connection state passed to the UI. */
data class CalendarUiState(
    val enabled: Boolean,
    val baseUrl: String,
    val token: String,
    val health: CalendarHealth?,
    val uploadState: UploadState
)

/** The current scan: the per-page JPEGs plus the ML Kit-generated PDF (if any). */
data class ScanResult(
    val pages: List<File>,
    val pdf: File?,
    val createdAt: Long
) {
    val pageCount: Int get() = pages.size

    fun deleteFiles() {
        pages.forEach { it.delete() }
        pdf?.delete()
    }
}

/** Holds the latest scan result, its on-device date extraction, and the settings toggle. */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ocr = OnDeviceOcrService()
    private var extractJob: Job? = null

    var result by mutableStateOf<ScanResult?>(null)
        private set

    var extracting by mutableStateOf(false)
        private set

    var ocrResult by mutableStateOf<OcrResult?>(null)
        private set

    var extractDatesEnabled by mutableStateOf(prefs.getBoolean(KEY_EXTRACT_DATES, false))
        private set

    private val calendar = CalendarClient()
    private var healthJob: Job? = null
    private var uploadJob: Job? = null

    var calendarEnabled by mutableStateOf(prefs.getBoolean(KEY_CAL_ENABLED, false))
        private set
    var calendarBaseUrl by mutableStateOf(prefs.getString(KEY_CAL_URL, DEFAULT_CAL_URL) ?: DEFAULT_CAL_URL)
        private set
    var calendarToken by mutableStateOf(prefs.getString(KEY_CAL_TOKEN, "") ?: "")
        private set

    /** Null while unknown/probing; set after a health check. */
    var calendarHealth by mutableStateOf<CalendarHealth?>(null)
        private set

    var uploadState by mutableStateOf<UploadState>(UploadState.Idle)
        private set

    // User corrections take precedence over what was extracted (or the scan-time fallback).
    private var correctedIsoDate by mutableStateOf<String?>(null)
    private var correctedIsoTime by mutableStateOf<String?>(null)

    private val extractedIsoDate: String? get() = (ocrResult as? OcrResult.Success)?.fields?.isoDate
    private val extractedIsoTime: String? get() = (ocrResult as? OcrResult.Success)?.fields?.isoTime

    /** The date used for display and the export filename: correction → extracted → scan date. */
    val effectiveIsoDate: String
        get() = correctedIsoDate ?: extractedIsoDate ?: formatStamp("yyyy-MM-dd")

    /** The time used for display and the export filename: correction → extracted → scan time. */
    val effectiveIsoTime: String
        get() = correctedIsoTime ?: extractedIsoTime ?: formatStamp("HHmm")

    /** The time read off the document (or set by hand), for the calendar to match the scan to an
     *  entry: never the scan-time fallback, which says nothing about when it was spent. */
    private val receiptIsoTime: String? get() = correctedIsoTime ?: extractedIsoTime

    /** A hint shown when the date wasn't detected (and the user hasn't set one yet), else null. */
    val dateNote: String?
        get() {
            if (correctedIsoDate != null) return null
            return when (val r = ocrResult) {
                is OcrResult.Success -> if (r.fields.isoDate == null) "No date detected — tap to set it" else null
                is OcrResult.Failure -> "Couldn't read the date — tap to set it"
                else -> null
            }
        }

    private fun formatStamp(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(result?.createdAt ?: System.currentTimeMillis()))

    fun correctDate(isoDate: String) { correctedIsoDate = isoDate }

    fun correctTime(isoTime: String) { correctedIsoTime = isoTime }

    fun update(newResult: ScanResult) {
        result?.deleteFiles()
        result = newResult
        resetExtraction()
        uploadState = UploadState.Idle
        if (extractDatesEnabled) extractFields()
        if (calendarEnabled) refreshCalendarHealth()
    }

    fun clear() {
        result?.deleteFiles()
        result = null
        resetExtraction()
        uploadState = UploadState.Idle
    }

    fun connectCalendar(enabled: Boolean) {
        if (enabled == calendarEnabled) return
        prefs.edit().putBoolean(KEY_CAL_ENABLED, enabled).apply()
        calendarEnabled = enabled
        if (enabled) refreshCalendarHealth() else calendarHealth = null
    }

    fun saveCalendarConfig(baseUrl: String, token: String) {
        calendarBaseUrl = baseUrl.trim()
        calendarToken = token.trim()
        prefs.edit()
            .putString(KEY_CAL_URL, calendarBaseUrl)
            .putString(KEY_CAL_TOKEN, calendarToken)
            .apply()
        if (calendarEnabled) refreshCalendarHealth()
    }

    /** Probe whether the calendar is reachable, so the UI can offer (or hide) upload. */
    fun refreshCalendarHealth() {
        if (!calendarEnabled || calendarBaseUrl.isBlank()) {
            calendarHealth = null
            return
        }
        healthJob?.cancel()
        calendarHealth = null
        healthJob = viewModelScope.launch {
            calendarHealth = calendar.checkHealth(calendarBaseUrl)
        }
    }

    fun uploadToCalendar() {
        val current = result ?: return
        if (calendarToken.isBlank()) {
            uploadState = UploadState.Error("Add an API token in Settings first.")
            return
        }
        // Single-page scans go up as a JPG; multi-page keeps the combined PDF so every page travels in one file.
        val pdf = current.pdf
        val useJpg = current.pages.size <= 1 || pdf == null
        val file = (if (useJpg) current.pages.firstOrNull() else pdf) ?: return
        val ext = if (useJpg) "jpg" else "pdf"
        val mime = if (useJpg) "image/jpeg" else "application/pdf"
        val fileName = "$effectiveIsoDate-$effectiveIsoTime.$ext"
        uploadJob?.cancel()
        uploadState = UploadState.Uploading
        uploadJob = viewModelScope.launch {
            uploadState = when (
                val r = calendar.upload(calendarBaseUrl, calendarToken, file, mime, fileName, effectiveIsoDate, receiptIsoTime)
            ) {
                is UploadResult.Success -> UploadState.Done
                is UploadResult.Failure -> UploadState.Error(r.message)
            }
        }
    }

    fun setExtractDates(enabled: Boolean) {
        if (enabled == extractDatesEnabled) return
        prefs.edit().putBoolean(KEY_EXTRACT_DATES, enabled).apply()
        extractDatesEnabled = enabled
        if (enabled) {
            if (result != null) extractFields()
        } else {
            resetExtraction()
        }
    }

    private fun extractFields() {
        val page = result?.pages?.firstOrNull() ?: return
        extractJob?.cancel()
        ocrResult = null
        extracting = true
        extractJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { PageImporter.decodeScaled(page, OCR_MAX) }
            ocrResult = if (bitmap != null) ocr.extract(bitmap)
            else OcrResult.Failure("Couldn't read the page image.")
            extracting = false
        }
    }

    private fun resetExtraction() {
        extractJob?.cancel()
        extractJob = null
        extracting = false
        ocrResult = null
        correctedIsoDate = null
        correctedIsoTime = null
    }

    override fun onCleared() {
        resetExtraction()
        healthJob?.cancel()
        uploadJob?.cancel()
        result?.deleteFiles()
        ocr.close()
        super.onCleared()
    }
}
