package com.josiel.organizeprocesso.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Campo de data com seletor Material3 (usado em datas de processo/fase). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    data: LocalDate?,
    onDataSelecionada: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onLimpar: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    var mostrarSeletor by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = data?.format(formatoData).orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("Não definido") },
        trailingIcon = {
            Row {
                if (enabled && onLimpar != null && data != null) {
                    TextButton(onClick = onLimpar) { Text("Limpar") }
                }
                TextButton(onClick = { if (enabled) mostrarSeletor = true }) { Text("Alterar") }
            }
        },
        modifier = modifier
    )

    if (mostrarSeletor) {
        val estadoData = rememberDatePickerState(
            initialSelectedDateMillis = (data ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarSeletor = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoData.selectedDateMillis?.let { millis ->
                        onDataSelecionada(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    mostrarSeletor = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSeletor = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoData)
        }
    }
}
