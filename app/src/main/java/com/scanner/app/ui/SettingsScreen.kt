package com.scanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanner.app.calendar.CalendarHealth

@Composable
fun SettingsScreen(
    extractDates: Boolean,
    onToggleExtractDates: (Boolean) -> Unit,
    calendar: CalendarUiState,
    onConnectCalendar: (Boolean) -> Unit,
    onSaveCalendarConfig: (String, String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(text = "Settings", color = Color.White, fontSize = 20.sp)
        }

        SettingRow(
            title = "Extract Dates (Beta)",
            subtitle = "Automatically read the document's date on-device after each scan. " +
                "The first run downloads a small model.",
            checked = extractDates,
            onCheckedChange = onToggleExtractDates
        )

        SettingRow(
            title = "Connect to calendar (Beta)",
            subtitle = "Upload a scan to your home calendar on its date. Needs the home " +
                "server reachable and an API token (generate one in the dashboard).",
            checked = calendar.enabled,
            onCheckedChange = onConnectCalendar
        )

        if (calendar.enabled) {
            CalendarConfig(
                calendar = calendar,
                onSave = onSaveCalendarConfig
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CalendarConfig(
    calendar: CalendarUiState,
    onSave: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(calendar.baseUrl) }
    var token by remember { mutableStateOf(calendar.token) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://pi.your-tailnet.ts.net") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API token") },
            singleLine = true,
            visualTransformation = if (token.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSave(url, token) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save & check connection") }

        val (statusText, statusColor) = when (calendar.health) {
            CalendarHealth.Up -> "Connected" to Color(0xFF00E0B0)
            CalendarHealth.Down -> "Can't reach the server — check the URL and that it's running." to Color(0xFFFF8A80)
            null -> "Checking…" to Color.White.copy(alpha = 0.7f)
        }
        Text(text = statusText, color = statusColor, fontSize = 13.sp)
    }
}
