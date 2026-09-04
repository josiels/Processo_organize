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
import com.josiel.organizeprocesso.data.repository.HistoricoFaseRepository
import com.josiel.organizeprocesso.data.repository.ItemRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao
import com.josiel.organizeprocesso.domain.usecase.acaoDesignacaoDisponivel
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    val tipoProcessoSimples: Boolean = false,
    val itens: List<ItemEntity> = emptyList(),
    val historico: List<HistoricoItemUi> = emptyList(),
    val designadoParaNome: String? = null,
    val designadoPorNome: String? = null,
    /** Semáforo de "dias parado na fase corrente" (limites da Fase). */
    val statusSemaforo: StatusSemaforo = StatusSemaforo.OK,
    val diasParado: Long = 0L,
    val statusSemaforoDesignacao: StatusSemaforo = StatusSemaforo.OK,
    val diasDesdeDesignado: Long? = null,
    val acaoDesignacao: AcaoDesignacao = AcaoDesignacao.NENHUMA,
    val podeEditar: Boolean = false,
    val perfisAtivos: List<PerfilEntity> = emptyList(),
    val designando: Boolean = false,
    val erro: String? = null
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
    private val historicoRepository = HistoricoFaseRepository(database, SupabaseSessionManager.client)
    private val itemRepository = ItemRepository(database.itemDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    private val _erro = MutableStateFlow<String?>(null)
    private val _designando = MutableStateFlow(false)

    // Guarda `concluir()` de ler um cache de itens ainda vazio (achado da
    // revisão final: mesmo risco de zerar valor_estimado_total já tratado em
    // ProcessoFormViewModel para o formulário de edição/criação).
    private val _itensSincronizados = MutableStateFlow(false)

    init {
        // Sync preguiçoso por processo: `processo_fase_historico` e `itens` não
        // entram no sync pós-login por processo, e o cache do Room é destrutivo
        // entre versões — sem isto a timeline, a aba Itens e o semáforo de fase
        // ficam vazios até alguma escrita incidental repopular a tabela.
        viewModelScope.launch {
            try {
                historicoRepository.sincronizar(processoId)
                itemRepository.sincronizar(processoId)
                _itensSincronizados.value = true
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }

    private val estadoBase: Flow<ProcessoDetalheUiState> = combine(
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
        val sessao = SupabaseSessionManager.perfilAtual.value
        val hoje = LocalDate.now()

        val tipoProcesso = processo?.let { tipoProcessoMap[it.tipoProcessoId] }
        val diasDesdeDesignado = processo?.designadoEm?.let {
            ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), hoje)
        }
        val statusSemaforoDesignacao = if (diasDesdeDesignado != null && tipoProcesso != null) {
            calcularSemaforo(diasDesdeDesignado, tipoProcesso.diasAlertaAtencao, tipoProcesso.diasAlertaCritico)
        } else {
            StatusSemaforo.OK
        }

        // Semáforo de fase: mesma conta de ProcessoListViewModel (dias desde a
        // entrada na fase corrente vs. limites da própria Fase). Spec do Plano
        // 2B, seção 4.2 pede os dois semáforos lado a lado aqui também.
        val faseAtual = processo?.let { faseMap[it.faseAtualId] }
        val historicoAtivo = historico.find { it.dataSaida == null }
        val diasParado = historicoAtivo?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
        val statusSemaforo = faseAtual?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
            ?: StatusSemaforo.OK

        ProcessoDetalheUiState(
            carregando = false,
            processo = processo,
            faseAtualNome = faseAtual?.nome ?: "",
            tipoProcessoNome = tipoProcesso?.nome ?: "",
            tipoProcessoSimples = tipoProcesso?.simples == true,
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
            statusSemaforo = statusSemaforo,
            diasParado = diasParado,
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
    }

    // O erro de escrita vive num fluxo próprio porque o estado principal é
    // derivado do Room (combine + stateIn) e não pode ser reatribuído.
    val uiState: StateFlow<ProcessoDetalheUiState> = combine(estadoBase, _erro, _designando) { estado, erro, designando ->
        estado.copy(erro = erro, designando = designando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessoDetalheUiState())

    fun designar(novoResponsavelId: String?, motivo: String? = null) {
        if (_designando.value) return
        _designando.value = true
        viewModelScope.launch {
            _erro.value = null
            try {
                processoRepository.designar(processoId, novoResponsavelId, motivo)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            } finally {
                _designando.value = false
            }
        }
    }

    /**
     * Conclui um processo de tipo simples direto, sem passar por
     * AvancarFaseScreen — reaproveita ProcessoRepository.atualizar() (spec de
     * tipo-processo-simples, seção 5). A linha ativa de processo_fase_historico
     * não é fechada: não há "próxima fase" para a qual avançar.
     */
    fun concluir() {
        if (!_itensSincronizados.value) {
            // Sem isso, salvar agora reescreveria valor_estimado_total a partir
            // de uma lista de itens possivelmente vazia (cache ainda não
            // sincronizado) — mesmo risco documentado em ProcessoFormViewModel.
            _erro.value = "Aguarde a sincronização terminar antes de concluir o processo."
            return
        }
        viewModelScope.launch {
            _erro.value = null
            try {
                val estado = uiState.value
                val processo = estado.processo ?: return@launch
                processoRepository.atualizar(
                    processo = processo.copy(statusGeral = StatusGeralProcesso.CONCLUIDO),
                    itensAtuais = estado.itens,
                    itensRemovidos = emptyList()
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }
}
