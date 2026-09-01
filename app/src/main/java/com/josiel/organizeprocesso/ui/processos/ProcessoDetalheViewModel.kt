package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Uma entrada da timeline de fases, já resolvida com nomes para exibição. */
data class HistoricoItemUi(
    val historico: ProcessoFaseHistoricoEntity,
    val faseNome: String,
    val responsavelNome: String?
)

data class ProcessoDetalheUiState(
    val carregando: Boolean = true,
    val processo: ProcessoEntity? = null,
    val faseAtualNome: String = "",
    val itens: List<ItemEntity> = emptyList(),
    val historico: List<HistoricoItemUi> = emptyList()
)

/** ViewModel de Detalhe do Processo — abas de dados gerais, itens, timeline e anexos (ROADMAP.md, passo 8). */
class ProcessoDetalheViewModel(
    application: Application,
    processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val uiState: StateFlow<ProcessoDetalheUiState> = combine(
        processoRepository.observarPorId(processoId),
        processoRepository.observarItens(processoId),
        historicoDao.observarPorProcesso(processoId),
        faseRepository.observarTodas(),
        perfilRepository.observarTodos()
    ) { processo, itens, historico, fases, perfis ->
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        ProcessoDetalheUiState(
            carregando = false,
            processo = processo,
            faseAtualNome = processo?.let { faseMap[it.faseAtualId]?.nome } ?: "",
            itens = itens,
            historico = historico
                .sortedByDescending { it.dataEntrada }
                .map { entrada ->
                    HistoricoItemUi(
                        historico = entrada,
                        faseNome = faseMap[entrada.faseId]?.nome ?: "—",
                        responsavelNome = entrada.responsavelId?.let { perfilMap[it]?.nome }
                    )
                }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessoDetalheUiState())
}
