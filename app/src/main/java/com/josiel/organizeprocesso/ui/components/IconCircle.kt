package com.josiel.organizeprocesso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícone dentro de um círculo com fundo pastel (DESIGN.md, seção 1).
 * Ex: IconCircle(backgroundColor = IndigoPastel) { Icon(Icons.Filled.Person, tint = Indigo600, ...) }
 */
@Composable
fun IconCircle(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = backgroundColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
