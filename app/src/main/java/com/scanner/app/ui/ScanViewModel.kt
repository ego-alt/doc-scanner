package com.scanner.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Holds the most recent scan so it survives configuration changes. */
class ScanViewModel : ViewModel() {

    var scanned by mutableStateOf<Bitmap?>(null)
        private set

    fun onScanned(bitmap: Bitmap) {
        scanned = bitmap
    }

    fun reset() {
        scanned = null
    }
}
