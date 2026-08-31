package com.josiel.organizeprocesso.ui.theme

import androidx.compose.ui.graphics.Color

// Cor primária — índigo/roxo (header, botões, item ativo do bottom nav)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)
val IndigoPastel = Color(0xFFE0E7FF)

// Cor de ação/destaque — laranja (ex: "Ver todos os processos")
val Orange500 = Color(0xFFF97316)
val Orange800 = Color(0xFF9A3412)
val OrangePastel = Color(0xFFFFEDD5)

// Semáforo de tempo parado na fase
val VerdeOk = Color(0xFF16A34A)
val VerdeOkPastel = Color(0xFFDCFCE7)
val AmareloAtencao = Color(0xFFF59E0B)
val AmareloAtencaoPastel = Color(0xFFFEF3C7)
val VermelhoCritico = Color(0xFFDC2626)
val VermelhoCriticoPastel = Color(0xFFFEE2E2)

// Superfícies e texto (tema claro)
val BackgroundLilas = Color(0xFFF5F4FB)
val SurfaceBranca = Color(0xFFFFFFFF)
val TextoPrimario = Color(0xFF1E1B2E)
val TextoSecundario = Color(0xFF6B7280)
val Contorno = Color(0xFFE5E3F1)

/**
 * Paleta de badges de fase (DESIGN.md, seção 3 — "cor própria da fase,
 * distinta do semáforo"). Fase não tem campo de cor no schema; a cor é
 * derivada de forma determinística (ver `faseBadgeColors` em StatusPill.kt).
 */
val FaseBadgeContainerColors = listOf(
    IndigoPastel,
    OrangePastel,
    VerdeOkPastel,
    Color(0xFFEDE9FE), // roxo pastel
    Color(0xFFCFFAFE), // ciano pastel
    Color(0xFFFCE7F3) // rosa pastel
)

val FaseBadgeContentColors = listOf(
    Indigo600,
    Orange800,
    VerdeOk,
    Color(0xFF7C3AED), // roxo
    Color(0xFF0E7490), // ciano
    Color(0xFFBE185D) // rosa
)
