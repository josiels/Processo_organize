package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/**
 * Top [limite] processos em andamento com semáforo de tempo parado
 * CRÍTICO, para o card "Processos críticos" do Dashboard (spec do
 * Dashboard Início, seção 7).
 */
fun selecionarProcessosCriticos(
    processos: List<ProcessoResumoDashboard>,
    limite: Int = 5
): List<ProcessoResumoDashboard> =
    processos
        .filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO && it.statusSemaforo == StatusSemaforo.CRITICO }
        .take(limite)
