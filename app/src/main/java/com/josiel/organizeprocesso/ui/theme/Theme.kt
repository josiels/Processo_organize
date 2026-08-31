package com.josiel.organizeprocesso.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// DESIGN.md define tema claro fixo — não há esquema escuro nem cor dinâmica.
private val OrganizeProcessoColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = SurfaceBranca,
    primaryContainer = IndigoPastel,
    onPrimaryContainer = Indigo700,
    secondary = Orange500,
    onSecondary = SurfaceBranca,
    secondaryContainer = OrangePastel,
    onSecondaryContainer = Orange800,
    background = BackgroundLilas,
    onBackground = TextoPrimario,
    surface = SurfaceBranca,
    onSurface = TextoPrimario,
    surfaceVariant = BackgroundLilas,
    onSurfaceVariant = TextoSecundario,
    outline = Contorno,
    error = VermelhoCritico,
    onError = SurfaceBranca,
    errorContainer = VermelhoCriticoPastel,
    onErrorContainer = VermelhoCritico
)

@Composable
fun Organize_ProcessoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OrganizeProcessoColorScheme,
        typography = Typography,
        content = content
    )
}
