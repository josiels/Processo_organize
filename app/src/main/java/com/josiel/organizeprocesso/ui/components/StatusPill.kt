package com.josiel.organizeprocesso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.AmareloAtencaoPastel
import com.josiel.organizeprocesso.ui.theme.FaseBadgeContainerColors
import com.josiel.organizeprocesso.ui.theme.FaseBadgeContentColors
import com.josiel.organizeprocesso.ui.theme.VerdeOk
import com.josiel.organizeprocesso.ui.theme.VerdeOkPastel
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import com.josiel.organizeprocesso.ui.theme.VermelhoCriticoPastel
import kotlin.math.absoluteValue

/**
 * Badge/pill genérico (DESIGN.md, seção 1) — usado tanto para o nome da fase
 * quanto para o status_geral do processo.
 */
@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Pill de badge pré-colorido a partir do semáforo de tempo parado. */
@Composable
fun SemaforoPill(
    status: StatusSemaforo,
    modifier: Modifier = Modifier
) {
    val (label, container, content) = when (status) {
        StatusSemaforo.OK -> Triple("Em dia", VerdeOkPastel, VerdeOk)
        StatusSemaforo.ATENCAO -> Triple("Atenção", AmareloAtencaoPastel, AmareloAtencao)
        StatusSemaforo.CRITICO -> Triple("Crítico", VermelhoCriticoPastel, VermelhoCritico)
    }
    StatusPill(text = label, containerColor = container, contentColor = content, modifier = modifier)
}

/** Pill de badge da fase, com cor derivada de forma determinística do id da fase. */
@Composable
fun FasePill(
    nome: String,
    faseId: String,
    modifier: Modifier = Modifier
) {
    val indice = (faseId.hashCode().absoluteValue) % FaseBadgeContainerColors.size
    StatusPill(
        text = nome,
        containerColor = FaseBadgeContainerColors[indice],
        contentColor = FaseBadgeContentColors[indice],
        modifier = modifier
    )
}
