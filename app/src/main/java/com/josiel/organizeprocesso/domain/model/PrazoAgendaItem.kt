package com.josiel.organizeprocesso.domain.model

import java.time.LocalDate

/**
 * Um processo com prazo na fase corrente, para a grade da Agenda (spec da
 * Agenda, seção 6).
 */
data class PrazoAgendaItem(
    val processoId: String,
    val numero: String,
    val objeto: String,
    val faseNome: String,
    val prazoLimite: LocalDate
)
