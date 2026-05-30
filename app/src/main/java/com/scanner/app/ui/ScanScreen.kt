package com.scanner.app.ui

import android.app.Activity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREVIEW_MAX = 1400

@Composable
fun ScanScreen(
    result: ScanResult?,
    onResult: (ScanResult) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

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
                    onScanAgain = { startScan() },
                    onShareImage = { ImageSaver.shareFile(context, it, "image/jpeg") },
                    onSharePdf = { ImageSaver.shareFile(context, it, "application/pdf") },
                    onClose = onClear
                )
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
    onScanAgain: () -> Unit,
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

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
