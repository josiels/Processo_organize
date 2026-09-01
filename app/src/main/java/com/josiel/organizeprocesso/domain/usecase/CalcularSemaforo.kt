package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/**
 * Semáforo de tempo decorrido comparado aos limites de alerta — mesma lógica
 * usada tanto para "dias parado na fase" (limites da Fase) quanto para "dias
 * desde designado" (limites do TipoProcesso). Spec do Plano 2B, seção 4.2.
 */
fun calcularSemaforo(diasDecorridos: Long, diasAlertaAtencao: Int, diasAlertaCritico: Int): StatusSemaforo = when {
    diasDecorridos >= diasAlertaCritico -> StatusSemaforo.CRITICO
    diasDecorridos >= diasAlertaAtencao -> StatusSemaforo.ATENCAO
    else -> StatusSemaforo.OK
}
