package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProcessoFormUiState(
    val carregando: Boolean = true,
    val numero: String = "",
    val objeto: String = "",
    val descricao: String = "",
    val orgaoDemandante: String = "",
    val tipo: String = "",
    val dataAbertura: LocalDate = LocalDate.now(),
    val faseSelecionadaId: String? = null,
    val statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
    val itens: List<ItemEntity> = emptyList(),
    /** Nomes das fases pelas quais o processo já passou (edição) — usado por RegrasBloqueioCampos. */
    val fasesPercorridasNomes: Set<String> = emptySet()
) {
    val valorEstimadoTotal: Double
        get() = itens.sumOf { it.quantidade * it.valorEstimadoUnit }

    val valido: Boolean
        get() = numero.isNotBlank() && objeto.isNotBlank() && faseSelecionadaId != null
}

/** ViewModel de criação/edição de Processo + Itens (ROADMAP.md, passo 6). */
class ProcessoFormViewModel(
    application: Application,
    private val processoId: String?
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)

    val ehEdicao: Boolean = processoId != null

    val fases: StateFlow<List<FaseEntity>> = faseRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ProcessoFormUiState())
    val uiState: StateFlow<ProcessoFormUiState> = _uiState.asStateFlow()

    private var processoOriginal: ProcessoEntity? = null
    private val itensRemovidos = mutableListOf<ItemEntity>()

    init {
        if (processoId != null) {
            viewModelScope.launch {
                val processo = processoRepository.observarPorId(processoId).first()
                val itens = processoRepository.observarItens(processoId).first()
                val historico = database.processoFaseHistoricoDao().observarPorProcesso(processoId).first()
                val faseMap = faseRepository.observarTodas().first().associateBy { it.id }
                val fasesPercorridasNomes = historico.mapNotNull { faseMap[it.faseId]?.nome }.toSet()
                if (processo != null) {
                    processoOriginal = processo
                    _uiState.value = ProcessoFormUiState(
                        carregando = false,
                        numero = processo.numero,
                        objeto = processo.objeto,
                        descricao = processo.descricao,
                        orgaoDemandante = processo.orgaoDemandante,
                        tipo = processo.tipoProcessoId,
                        dataAbertura = processo.dataAbertura,
                        faseSelecionadaId = processo.faseAtualId,
                        statusGeral = processo.statusGeral,
                        itens = itens,
                        fasesPercorridasNomes = fasesPercorridasNomes
                    )
                } else {
                    _uiState.value = _uiState.value.copy(carregando = false)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(carregando = false)
        }
    }

    fun atualizarNumero(valor: String) {
        _uiState.value = _uiState.value.copy(numero = valor)
    }

    fun atualizarObjeto(valor: String) {
        _uiState.value = _uiState.value.copy(objeto = valor)
    }

    fun atualizarDescricao(valor: String) {
        _uiState.value = _uiState.value.copy(descricao = valor)
    }

    fun atualizarOrgaoDemandante(valor: String) {
        _uiState.value = _uiState.value.copy(orgaoDemandante = valor)
    }

    fun atualizarTipo(valor: String) {
        _uiState.value = _uiState.value.copy(tipo = valor)
    }

    fun atualizarDataAbertura(valor: LocalDate) {
        _uiState.value = _uiState.value.copy(dataAbertura = valor)
    }

    fun atualizarFase(faseId: String) {
        _uiState.value = _uiState.value.copy(faseSelecionadaId = faseId)
    }

    fun atualizarStatusGeral(status: StatusGeralProcesso) {
        _uiState.value = _uiState.value.copy(statusGeral = status)
    }

    fun salvarItem(
        itemExistente: ItemEntity?,
        descricao: String,
        quantidade: Double,
        unidade: String,
        valorEstimadoUnit: Double,
        valorPesquisaUnit: Double?
    ) {
        val item = (itemExistente ?: ItemEntity(
            id = UUID.randomUUID().toString(),
            processoId = processoId.orEmpty(),
            descricao = descricao,
            quantidade = quantidade,
            unidade = unidade,
            valorEstimadoUnit = valorEstimadoUnit,
            valorPesquisaUnit = valorPesquisaUnit
        )).copy(
            descricao = descricao,
            quantidade = quantidade,
            unidade = unidade,
            valorEstimadoUnit = valorEstimadoUnit,
            valorPesquisaUnit = valorPesquisaUnit
        )

        val atual = _uiState.value.itens.toMutableList()
        val index = atual.indexOfFirst { it.id == item.id }
        if (index >= 0) atual[index] = item else atual.add(item)
        _uiState.value = _uiState.value.copy(itens = atual)
    }

    fun removerItem(item: ItemEntity) {
        val atual = _uiState.value.itens.toMutableList()
        atual.remove(item)
        _uiState.value = _uiState.value.copy(itens = atual)
        // Se o item já existia no processo (id não gerado nesta sessão), precisa
        // ser removido do Room ao salvar. Excluir um id que nunca foi persistido
        // é um no-op inofensivo, então não há necessidade de rastrear a origem.
        if (ehEdicao) itensRemovidos.add(item)
    }

    fun salvar(onSalvo: (String) -> Unit) {
        val estado = _uiState.value
        val faseId = estado.faseSelecionadaId ?: return
        if (!estado.valido) return

        viewModelScope.launch {
            val id = if (ehEdicao) {
                val original = processoOriginal ?: return@launch
                processoRepository.atualizar(
                    processo = original.copy(
                        numero = estado.numero,
                        objeto = estado.objeto,
                        descricao = estado.descricao,
                        orgaoDemandante = estado.orgaoDemandante,
                        tipoProcessoId = estado.tipo,
                        dataAbertura = estado.dataAbertura,
                        faseAtualId = faseId,
                        statusGeral = estado.statusGeral
                    ),
                    itensAtuais = estado.itens,
                    itensRemovidos = itensRemovidos
                )
                original.id
            } else {
                processoRepository.criar(
                    organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty(),
                    numero = estado.numero,
                    objeto = estado.objeto,
                    descricao = estado.descricao,
                    orgaoDemandante = estado.orgaoDemandante,
                    tipoProcessoId = estado.tipo,
                    dataAbertura = estado.dataAbertura,
                    faseInicialId = faseId,
                    statusGeral = estado.statusGeral,
                    itens = estado.itens
                )
            }
            onSalvo(id)
        }
    }
}
