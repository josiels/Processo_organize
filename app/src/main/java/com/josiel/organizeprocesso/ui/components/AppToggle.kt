package com.josiel.organizeprocesso.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.josiel.organizeprocesso.ui.theme.VerdeOk

/**
 * Toggle liga/desliga padrão do app (DESIGN.md, seção 8) — usado para todas
 * as opções binárias (ex: "Notificar sobre prazo desta fase?"). Nunca usar
 * checkbox tradicional para esse tipo de opção.
 */
@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = VerdeOk,
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface
        )
    )
}
