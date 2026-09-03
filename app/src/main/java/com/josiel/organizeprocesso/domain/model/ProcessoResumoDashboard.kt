package com.josiel.organizeprocesso.domain.model

import java.time.LocalDate

/**
 * Vista simplificada de um processo para as agregações do Dashboard
 * (spec do Dashboard Início, seção 7) — não reusa `ProcessoListItem`
 * (`ui.processos`) para manter `ui.inicio` desacoplado de outra tela.
 */
data class ProcessoResumoDashboard(
    val id: String,
    val numero: String,
    val objeto: String,
    val statusGeral: StatusGeralProcesso,
    val statusSemaforo: StatusSemaforo,
    val prazoLimite: LocalDate?
)
