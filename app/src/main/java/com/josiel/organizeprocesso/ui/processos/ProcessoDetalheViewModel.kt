package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao
import com.josiel.organizeprocesso.domain.usecase.acaoDesignacaoDisponivel
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val tipoProcessoNome: String = "",
    val itens: List<ItemEntity> = emptyList(),
    val historico: List<HistoricoItemUi> = emptyList(),
    val designadoParaNome: String? = null,
    val designadoPorNome: String? = null,
    val statusSemaforoDesignacao: StatusSemaforo = StatusSemaforo.OK,
    val diasDesdeDesignado: Long? = null,
    val acaoDesignacao: AcaoDesignacao = AcaoDesignacao.NENHUMA,
    val podeEditar: Boolean = false,
    val perfisAtivos: List<PerfilEntity> = emptyList()
)

/** ViewModel de Detalhe do Processo (spec do Plano 2B, seção 4.3). */
class ProcessoDetalheViewModel(
    application: Application,
    private val processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val tipoProcessoRepository = TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val uiState: StateFlow<ProcessoDetalheUiState> = combine(
        processoRepository.observarPorId(processoId),
        processoRepository.observarItens(processoId),
        historicoDao.observarPorProcesso(processoId),
        faseRepository.observarTodas(),
        combine(perfilRepository.observarTodos(), tipoProcessoRepository.observarTodas()) { perfis, tiposProcesso ->
            perfis to tiposProcesso
        }
    ) { processo, itens, historico, fases, perfisETipos ->
        val (perfis, tiposProcesso) = perfisETipos
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        val tipoProcessoMap = tiposProcesso.associateBy { it.id }
        val sessao = SupabaseSessionManager.perfilAtual

        val tipoProcesso = processo?.let { tipoProcessoMap[it.tipoProcessoId] }
        val diasDesdeDesignado = processo?.designadoEm?.let {
            ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
        }
        val statusSemaforoDesignacao = if (diasDesdeDesignado != null && tipoProcesso != null) {
            calcularSemaforo(diasDesdeDesignado, tipoProcesso.diasAlertaAtencao, tipoProcesso.diasAlertaCritico)
        } else {
            StatusSemaforo.OK
        }

        ProcessoDetalheUiState(
            carregando = false,
            processo = processo,
            faseAtualNome = processo?.let { faseMap[it.faseAtualId]?.nome } ?: "",
            tipoProcessoNome = tipoProcesso?.nome ?: "",
            itens = itens,
            historico = historico
                .sortedByDescending { it.dataEntrada }
                .map { entrada ->
                    HistoricoItemUi(
                        historico = entrada,
                        faseNome = faseMap[entrada.faseId]?.nome ?: "—",
                        responsavelNome = entrada.responsavelId?.let { perfilMap[it]?.nome }
                    )
                },
            designadoParaNome = processo?.responsavelId?.let { perfilMap[it]?.nome },
            designadoPorNome = processo?.designadoPor?.let { perfilMap[it]?.nome },
            statusSemaforoDesignacao = statusSemaforoDesignacao,
            diasDesdeDesignado = diasDesdeDesignado,
            acaoDesignacao = if (processo != null && sessao != null) {
                acaoDesignacaoDisponivel(sessao.papel, processo.responsavelId, sessao.id)
            } else {
                AcaoDesignacao.NENHUMA
            },
            podeEditar = processo != null && sessao != null &&
                podeEditarProcesso(sessao.papel, processo.responsavelId, sessao.id),
            perfisAtivos = perfis.filter { it.ativo }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessoDetalheUiState())

    fun designar(novoResponsavelId: String?) {
        viewModelScope.launch { processoRepository.designar(processoId, novoResponsavelId) }
    }
}
