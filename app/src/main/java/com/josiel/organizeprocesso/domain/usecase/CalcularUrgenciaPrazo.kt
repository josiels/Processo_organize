package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Urgência de um [prazo] em relação a [hoje] — vencido ou vence hoje é
 * CRÍTICO, dentro dos próximos 5 dias é ATENÇÃO, mais adiante é OK (spec
 * da Agenda, seção 5). Eixo diferente do semáforo de "tempo parado na
 * fase" (CalcularSemaforo.kt) — este mede proximidade de um prazo, não
 * duração numa fase.
 */
fun calcularUrgenciaPrazo(prazo: LocalDate, hoje: LocalDate): StatusSemaforo {
    val dias = ChronoUnit.DAYS.between(hoje, prazo)
    return when {
        dias <= 0 -> StatusSemaforo.CRITICO
        dias <= 5 -> StatusSemaforo.ATENCAO
        else -> StatusSemaforo.OK
    }
}
