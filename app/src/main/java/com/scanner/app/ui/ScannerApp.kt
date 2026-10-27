package com.scanner.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanner.app.update.install

@Composable
fun ScannerApp(viewModel: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    ScanScreen(
        result = viewModel.result,
        extracting = viewModel.extracting,
        extractDatesEnabled = viewModel.extractDatesEnabled,
        isoDate = viewModel.effectiveIsoDate,
        isoTime = viewModel.effectiveIsoTime,
        dateNote = viewModel.dateNote,
        calendar = CalendarUiState(
            enabled = viewModel.calendarEnabled,
            baseUrl = viewModel.calendarBaseUrl,
            token = viewModel.calendarToken,
            health = viewModel.calendarHealth,
            uploadState = viewModel.uploadState
        ),
        onResult = viewModel::update,
        onSetExtractDates = viewModel::setExtractDates,
        onCorrectDate = viewModel::correctDate,
        onCorrectTime = viewModel::correctTime,
        onConnectCalendar = viewModel::connectCalendar,
        onSaveCalendarConfig = viewModel::saveCalendarConfig,
        onUpload = viewModel::uploadToCalendar,
        onClear = viewModel::clear,
        update = viewModel.availableUpdate,
        updateState = viewModel.updateState,
        onUpdate = { viewModel.downloadUpdate { apk -> install(context, apk) } }
    )
}
