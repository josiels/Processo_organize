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
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Item de UI da lista de Processos: dados-mãe + informações derivadas da fase corrente. */
data class ProcessoListItem(
    val processo: ProcessoEntity,
    val faseNome: String,
    val responsavelNome: String?,
    val statusSemaforo: StatusSemaforo,
    val diasParado: Long
)

/** ViewModel da lista de Processos (REQUISITOS.md, seção 8; DESIGN.md, seção 3; ROADMAP.md, passo 7). */
class ProcessoListViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val itens: StateFlow<List<ProcessoListItem>> = combine(
        processoRepository.observarTodos(),
        faseRepository.observarTodas(),
        perfilRepository.observarTodos(),
        historicoDao.observarTodosAtivos()
    ) { processos, fases, perfis, historicosAtivos ->
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        val historicoPorProcesso = historicosAtivos.associateBy { it.processoId }
        val hoje = LocalDate.now()

        processos.map { processo ->
            val historico = historicoPorProcesso[processo.id]
            val fase = faseMap[processo.faseAtualId]
            val diasParado = historico?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
            val statusSemaforo = when {
                fase == null -> StatusSemaforo.OK
                diasParado >= fase.diasAlertaCritico -> StatusSemaforo.CRITICO
                diasParado >= fase.diasAlertaAtencao -> StatusSemaforo.ATENCAO
                else -> StatusSemaforo.OK
            }
            ProcessoListItem(
                processo = processo,
                faseNome = fase?.nome ?: "—",
                responsavelNome = historico?.responsavelId?.let { perfilMap[it]?.nome },
                statusSemaforo = statusSemaforo,
                diasParado = diasParado
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
