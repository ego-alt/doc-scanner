package com.scanner.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScannerApp(viewModel: ScanViewModel = viewModel()) {
    ScanScreen(
        result = viewModel.result,
        onResult = viewModel::update,
        onClear = viewModel::clear
    )
}
