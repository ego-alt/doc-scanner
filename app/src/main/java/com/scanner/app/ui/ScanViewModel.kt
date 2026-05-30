package com.scanner.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

/** The current scan: the per-page JPEGs plus the ML Kit-generated PDF (if any). */
data class ScanResult(
    val pages: List<File>,
    val pdf: File?
) {
    val pageCount: Int get() = pages.size

    fun deleteFiles() {
        pages.forEach { it.delete() }
        pdf?.delete()
    }
}

/** Holds the most recent scan result; one ML Kit session at a time. */
class ScanViewModel : ViewModel() {

    var result by mutableStateOf<ScanResult?>(null)
        private set

    fun update(newResult: ScanResult) {
        result?.deleteFiles()
        result = newResult
    }

    fun clear() {
        result?.deleteFiles()
        result = null
    }

    override fun onCleared() {
        result?.deleteFiles()
        super.onCleared()
    }
}
