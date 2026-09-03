package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.LocalDate

/**
 * Top [limite] processos em andamento com `prazoLimite` mais próximo (a
 * partir de [hoje]), excluindo prazos já vencidos — um prazo vencido não
 * é "próximo", já fica coberto pelo sinal de "críticos" se cruzar o
 * limiar de alerta da fase (spec do Dashboard Início, seção 7).
 */
fun selecionarProximosPrazos(
    processos: List<ProcessoResumoDashboard>,
    hoje: LocalDate,
    limite: Int = 5
): List<ProcessoResumoDashboard> =
    processos
        .filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO && it.prazoLimite != null && !it.prazoLimite.isBefore(hoje) }
        .sortedBy { it.prazoLimite }
        .take(limite)
