package com.josiel.organizeprocesso.ui.theme

import androidx.compose.ui.graphics.Color
import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/** Cor sólida associada a cada semáforo — compartilhada por todo lugar que pinta um StatusSemaforo. */
fun StatusSemaforo.cor(): Color = when (this) {
    StatusSemaforo.OK -> VerdeOk
    StatusSemaforo.ATENCAO -> AmareloAtencao
    StatusSemaforo.CRITICO -> VermelhoCritico
}
