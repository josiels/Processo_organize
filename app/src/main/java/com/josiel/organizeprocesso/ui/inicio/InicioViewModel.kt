package com.josiel.organizeprocesso.ui.inicio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.ProgressoGeral
import com.josiel.organizeprocesso.domain.usecase.calcularProgressoGeral
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.selecionarProcessosCriticos
import com.josiel.organizeprocesso.domain.usecase.selecionarProximosPrazos
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InicioUiState(
    val carregando: Boolean = true,
    val saudacao: String = "",
    val progresso: ProgressoGeral = ProgressoGeral(0, 0),
    val processosAtualizadosHoje: Int = 0,
    val processosCriticos: List<ProcessoResumoDashboard> = emptyList(),
    val proximosPrazos: List<ProcessoResumoDashboard> = emptyList()
)

/** ViewModel do Dashboard Início (spec do Dashboard Início, seções 5-8). */
class InicioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val uiState: StateFlow<InicioUiState> = combine(
        processoRepository.observarTodos(),
        faseRepository.observarTodas(),
        historicoDao.observarTodos(),
        perfilRepository.observarTodos()
    ) { processos, fases, historicoCompleto, perfis ->
        val faseMap = fases.associateBy { it.id }
        // observarTodosAtivos() era um flow separado, mas seu filtro
        // (dataSaida == null) é um subconjunto estrito do que historicoCompleto
        // já traz — derivar em memória evita uma segunda query no Room e uma
        // recomputação duplicada do combine a cada escrita em
        // processo_fase_historico (achado da revisão final do Dashboard Início).
        val historicoAtivoPorProcesso = historicoCompleto.filter { it.dataSaida == null }.associateBy { it.processoId }
        val hoje = LocalDate.now()

        val resumos = processos.map { processo ->
            val historicoAtivo = historicoAtivoPorProcesso[processo.id]
            val fase = faseMap[processo.faseAtualId]
            val diasParado = historicoAtivo?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
            val statusSemaforo = fase?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                ?: StatusSemaforo.OK
            ProcessoResumoDashboard(
                id = processo.id,
                numero = processo.numero,
                objeto = processo.objeto,
                statusGeral = processo.statusGeral,
                statusSemaforo = statusSemaforo,
                prazoLimite = historicoAtivo?.prazoLimite
            )
        }

        val idsComHistoricoHoje = historicoCompleto.filter { it.dataEntrada == hoje }.map { it.processoId }.toSet()
        val idsComAtualizacaoHoje = processos
            .filter { it.atualizadoEm.atZone(ZoneId.systemDefault()).toLocalDate() == hoje }
            .map { it.id }
            .toSet()

        val nomePerfil = perfis.find { it.id == SupabaseSessionManager.perfilAtual.value?.id }?.nome

        InicioUiState(
            carregando = false,
            saudacao = saudacaoPorHorario() + (nomePerfil?.let { ", $it!" } ?: "!"),
            progresso = calcularProgressoGeral(resumos),
            processosAtualizadosHoje = (idsComHistoricoHoje + idsComAtualizacaoHoje).size,
            processosCriticos = selecionarProcessosCriticos(resumos),
            proximosPrazos = selecionarProximosPrazos(resumos, hoje)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())
}

private fun saudacaoPorHorario(): String {
    val hora = LocalTime.now().hour
    return when {
        hora < 12 -> "Bom dia"
        hora < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}
