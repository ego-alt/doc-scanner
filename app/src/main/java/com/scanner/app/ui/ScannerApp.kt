package com.scanner.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScannerApp(viewModel: ScanViewModel = viewModel()) {
    val scanned = viewModel.scanned
    if (scanned == null) {
        HomeScreen(onScanned = viewModel::onScanned)
    } else {
        ResultScreen(
            bitmap = scanned,
            onNewScan = viewModel::reset
        )
    }
}
