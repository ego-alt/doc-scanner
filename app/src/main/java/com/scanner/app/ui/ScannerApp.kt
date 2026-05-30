package com.scanner.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScannerApp(viewModel: ScanViewModel = viewModel()) {
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
        onClear = viewModel::clear
    )
}
