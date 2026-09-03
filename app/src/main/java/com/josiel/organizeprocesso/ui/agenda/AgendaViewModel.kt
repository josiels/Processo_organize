package com.josiel.organizeprocesso.ui.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.PrazoAgendaItem
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ViewModel da Agenda (spec da Agenda, seção 6). */
class AgendaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val prazos: StateFlow<List<PrazoAgendaItem>> = combine(
        processoRepository.observarTodos(),
        historicoDao.observarTodosAtivos(),
        faseRepository.observarTodas()
    ) { processos, historicosAtivos, fases ->
        val faseMap = fases.associateBy { it.id }
        val historicoPorProcesso = historicosAtivos.associateBy { it.processoId }

        processos.mapNotNull { processo ->
            if (processo.statusGeral != StatusGeralProcesso.EM_ANDAMENTO) return@mapNotNull null
            val historico = historicoPorProcesso[processo.id] ?: return@mapNotNull null
            val prazoLimite = historico.prazoLimite ?: return@mapNotNull null
            PrazoAgendaItem(
                processoId = processo.id,
                numero = processo.numero,
                objeto = processo.objeto,
                faseNome = faseMap[historico.faseId]?.nome ?: "—",
                prazoLimite = prazoLimite
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
