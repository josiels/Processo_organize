package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.domain.model.Papel
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProcessoListItem(
    val processo: ProcessoEntity,
    val faseNome: String,
    val responsavelNome: String,
    val statusSemaforo: StatusSemaforo,
    val diasParado: Long,
    val statusSemaforoDesignacao: StatusSemaforo,
    val diasDesdeDesignado: Long?
)

/** ViewModel da lista de Processos (REQUISITOS.md, seção 8; spec do Plano 2B, seção 4.2). */
class ProcessoListViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val tipoProcessoRepository = TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val itens: StateFlow<List<ProcessoListItem>> = combine(
        processoRepository.observarTodos(),
        faseRepository.observarTodas(),
        perfilRepository.observarTodos(),
        historicoDao.observarTodosAtivos(),
        tipoProcessoRepository.observarTodas()
    ) { processos, fases, perfis, historicosAtivos, tiposProcesso ->
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        val tipoProcessoMap = tiposProcesso.associateBy { it.id }
        val historicoPorProcesso = historicosAtivos.associateBy { it.processoId }
        val hoje = LocalDate.now()
        val sessao = SupabaseSessionManager.perfilAtual.value

        // Usuário comum só vê os processos dos quais é responsável — admin
        // continua vendo tudo (é a única tela que lista todo mundo pra poder
        // designar/gerenciar; SUPER_ADMIN não tem tratamento especial em
        // nenhum lugar do app, então cai na mesma regra de usuário comum).
        processos
            .filter { processo -> sessao == null || sessao.papel == Papel.ADMIN || processo.responsavelId == sessao.id }
            .map { processo ->
                val historico = historicoPorProcesso[processo.id]
                val fase = faseMap[processo.faseAtualId]
                val diasParado = historico?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
                val statusSemaforo = fase?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                    ?: StatusSemaforo.OK

                val tipoProcesso = tipoProcessoMap[processo.tipoProcessoId]
                val diasDesdeDesignado = processo.designadoEm?.let {
                    ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), hoje)
                }
                val statusSemaforoDesignacao = if (diasDesdeDesignado != null && tipoProcesso != null) {
                    calcularSemaforo(diasDesdeDesignado, tipoProcesso.diasAlertaAtencao, tipoProcesso.diasAlertaCritico)
                } else {
                    StatusSemaforo.OK
                }

                ProcessoListItem(
                    processo = processo,
                    faseNome = fase?.nome ?: "—",
                    responsavelNome = processo.responsavelId?.let { perfilMap[it]?.nome } ?: "Não designado",
                    statusSemaforo = statusSemaforo,
                    diasParado = diasParado,
                    statusSemaforoDesignacao = statusSemaforoDesignacao,
                    diasDesdeDesignado = diasDesdeDesignado
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
