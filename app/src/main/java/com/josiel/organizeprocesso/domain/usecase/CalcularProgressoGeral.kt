package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/** Progresso geral do card do Dashboard (spec do Dashboard Início, seção 6). */
data class ProgressoGeral(val emDia: Int, val total: Int) {
    val percentual: Int get() = if (total == 0) 0 else (emDia * 100) / total
}

/**
 * "X de Y processos em dia" — Y é só processos em andamento (concluídos/
 * cancelados/suspensos saem do cálculo); X é quantos desses têm semáforo
 * de tempo parado na fase OK (spec do Dashboard Início, seção 6).
 */
fun calcularProgressoGeral(processos: List<ProcessoResumoDashboard>): ProgressoGeral {
    val emAndamento = processos.filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO }
    val emDia = emAndamento.count { it.statusSemaforo == StatusSemaforo.OK }
    return ProgressoGeral(emDia = emDia, total = emAndamento.size)
}
