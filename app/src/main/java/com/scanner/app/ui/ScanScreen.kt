package com.scanner.app.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanner.app.calendar.CalendarHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val PREVIEW_MAX = 1400

@Composable
fun ScanScreen(
    result: ScanResult?,
    extracting: Boolean,
    extractDatesEnabled: Boolean,
    isoDate: String,
    isoTime: String,
    dateNote: String?,
    calendar: CalendarUiState,
    onResult: (ScanResult) -> Unit,
    onSetExtractDates: (Boolean) -> Unit,
    onCorrectDate: (String) -> Unit,
    onCorrectTime: (String) -> Unit,
    onConnectCalendar: (Boolean) -> Unit,
    onSaveCalendarConfig: (String, String) -> Unit,
    onUpload: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val imageUris = scan?.pages?.map { it.imageUri } ?: emptyList()
            val pdfUri = scan?.pdf?.uri
            if (imageUris.isNotEmpty()) {
                scope.launch {
                    busy = true
                    val imported = withContext(Dispatchers.IO) {
                        PageImporter.importResult(context, imageUris, pdfUri)
                    }
                    busy = false
                    if (imported != null) onResult(imported)
                    else Toast.makeText(context, "Couldn't import the scan.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    fun startScan() {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Unable to start the scanner.", Toast.LENGTH_LONG).show()
            return
        }
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    e.localizedMessage ?: "Scanner unavailable. Update Google Play services.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val current = result
            if (current == null) {
                EmptyState(onScan = { startScan() }, modifier = Modifier.align(Alignment.Center))
            } else {
                ResultContent(
                    result = current,
                    busy = busy,
                    extracting = extracting,
                    showDate = extractDatesEnabled,
                    isoDate = isoDate,
                    isoTime = isoTime,
                    dateNote = dateNote,
                    canUpload = calendar.enabled && calendar.health == CalendarHealth.Up,
                    uploadState = calendar.uploadState,
                    onScanAgain = { startScan() },
                    onEditDate = { showDatePicker(context, isoDate, onCorrectDate) },
                    onEditTime = { showTimePicker(context, isoTime, onCorrectTime) },
                    onUpload = onUpload,
                    onShareImage = { file ->
                        ImageSaver.shareFile(context, file, "image/jpeg", "$isoDate-$isoTime.jpg")
                    },
                    onSharePdf = { file ->
                        ImageSaver.shareFile(context, file, "application/pdf", "$isoDate-$isoTime.pdf")
                    },
                    onClose = onClear
                )
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow taps while working */ },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        if (showSettings) {
            SettingsScreen(
                extractDates = extractDatesEnabled,
                onToggleExtractDates = onSetExtractDates,
                calendar = calendar,
                onConnectCalendar = onConnectCalendar,
                onSaveCalendarConfig = onSaveCalendarConfig,
                onClose = { showSettings = false }
            )
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.DocumentScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Text(text = "Document Scanner", color = Color.White, fontSize = 24.sp)
        Text(
            text = "Scan a document, then share it as an image or PDF.",
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onScan) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Scan a document")
        }
    }
}

@Composable
private fun ResultContent(
    result: ScanResult,
    busy: Boolean,
    extracting: Boolean,
    showDate: Boolean,
    isoDate: String,
    isoTime: String,
    dateNote: String?,
    canUpload: Boolean,
    uploadState: UploadState,
    onScanAgain: () -> Unit,
    onEditDate: () -> Unit,
    onEditTime: () -> Unit,
    onUpload: () -> Unit,
    onShareImage: (File) -> Unit,
    onSharePdf: (File) -> Unit,
    onClose: () -> Unit
) {
    val singlePage = result.pageCount == 1
    val pdf = result.pdf
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, enabled = !busy) {
                Icon(Icons.Filled.Close, contentDescription = "Discard scan", tint = Color.White)
            }
            Text(
                text = if (singlePage) "1 page" else "${result.pageCount} pages",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(result.pages, key = { it.absolutePath }) { file ->
                PagePreview(file)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showDate) {
                DateSection(
                    extracting = extracting,
                    isoDate = isoDate,
                    isoTime = isoTime,
                    note = dateNote,
                    enabled = !busy,
                    onEditDate = onEditDate,
                    onEditTime = onEditTime
                )
            }
            if (canUpload) {
                UploadSection(uploadState = uploadState, onUpload = onUpload)
            }
            if (singlePage) {
                Button(
                    onClick = { onShareImage(result.pages.first()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share as image") }
                if (pdf != null) {
                    OutlinedButton(
                        onClick = { onSharePdf(pdf) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Share as PDF") }
                }
            } else if (pdf != null) {
                Button(
                    onClick = { onSharePdf(pdf) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share as PDF") }
            }
            OutlinedButton(
                onClick = onScanAgain,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Scan again") }
        }
    }
}

@Composable
private fun DateSection(
    extracting: Boolean,
    isoDate: String,
    isoTime: String,
    note: String?,
    enabled: Boolean,
    onEditDate: () -> Unit,
    onEditTime: () -> Unit
) {
    if (extracting) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Text("Reading date…", color = Color.White)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (note != null) {
            Text(text = note, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEditDate, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(isoDate)
            }
            OutlinedButton(onClick = onEditTime, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(formatTime(isoTime))
            }
        }
    }
}

@Composable
private fun UploadSection(uploadState: UploadState, onUpload: () -> Unit) {
    when (uploadState) {
        UploadState.Uploading -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Uploading…")
            }
        }
        UploadState.Done -> {
            Text("Uploaded to calendar ✓", color = Color(0xFF00E0B0), fontSize = 14.sp)
            OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                Text("Upload again")
            }
        }
        is UploadState.Error -> {
            Text(uploadState.message, color = Color(0xFFFF8A80), fontSize = 13.sp)
            Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                Text("Upload to calendar")
            }
        }
        UploadState.Idle -> {
            Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                Text("Upload to calendar")
            }
        }
    }
}

@Composable
private fun PagePreview(file: File) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { PageImporter.decodeScaled(file, PREVIEW_MAX) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Scanned page",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/** Display "HHmm" as "HH:mm". */
private fun formatTime(isoTime: String): String =
    if (isoTime.length == 4) "${isoTime.substring(0, 2)}:${isoTime.substring(2, 4)}" else isoTime

/** Platform date picker, seeded from [isoDate] ("yyyy-MM-dd"); reports back the same format. */
private fun showDatePicker(context: Context, isoDate: String, onPicked: (String) -> Unit) {
    val parts = isoDate.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2000
    val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 12)
    val day = (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 31)
    DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
        year,
        month - 1,
        day
    ).show()
}

/** Platform time picker, seeded from [isoTime] ("HHmm"); reports back the same format. */
private fun showTimePicker(context: Context, isoTime: String, onPicked: (String) -> Unit) {
    val hour = (isoTime.take(2).toIntOrNull() ?: 0).coerceIn(0, 23)
    val minute = (isoTime.drop(2).take(2).toIntOrNull() ?: 0).coerceIn(0, 59)
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(String.format(Locale.US, "%02d%02d", h, m)) },
        hour,
        minute,
        is24Hour
    ).show()
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
