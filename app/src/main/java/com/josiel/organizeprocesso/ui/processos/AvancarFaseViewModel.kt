@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.DiligenciaRepository
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.HistoricoFaseRepository
import com.josiel.organizeprocesso.data.repository.ItemRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val LIMITE_CARACTERES_OBSERVACAO = 500

data class AvancarFaseUiState(
    val carregando: Boolean = true,
    val processo: ProcessoEntity? = null,
    val faseAtual: FaseEntity? = null,
    val historicoAtual: ProcessoFaseHistoricoEntity? = null,
    val statusSemaforo: StatusSemaforo = StatusSemaforo.OK,
    val fases: List<FaseEntity> = emptyList(),
    val perfis: List<PerfilEntity> = emptyList(),
    val observacao: String = "",
    val executorId: String? = null,
    val prazoLimite: LocalDate? = null,
    val notificarPrazo: Boolean = false,
    val podeEditar: Boolean = false,
    val erro: String? = null
)

/** ViewModel da tela Avançar/Retroceder Fase (spec do Plano 2B, seção 4.4). */
class AvancarFaseViewModel(
    application: Application,
    private val processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoRepository = HistoricoFaseRepository(database, SupabaseSessionManager.client)
    private val diligenciaRepository = DiligenciaRepository(database.diligenciaDao(), SupabaseSessionManager.client)
    private val itemRepository = ItemRepository(database.itemDao(), SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(AvancarFaseUiState())
    val uiState: StateFlow<AvancarFaseUiState> = _uiState.asStateFlow()

    val diligencias: StateFlow<List<DiligenciaEntity>> = historicoRepository.observarAtivoPorProcesso(processoId)
        .flatMapLatest { historico -> historico?.let { diligenciaRepository.observarPorHistorico(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var sementeAplicada = false
    private var historicoSincronizado: String? = null

    init {
        // Sync preguiçoso por processo: sem isto `historicoAtual` fica null para
        // sempre num cache recém-criado (o Room é destrutivo entre versões e o
        // sync pós-login não puxa estas tabelas por processo), o ícone Salvar
        // fica desabilitado e as diligências nunca sincronizam.
        viewModelScope.launch {
            try {
                historicoRepository.sincronizar(processoId)
                itemRepository.sincronizar(processoId)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _uiState.value = _uiState.value.copy(erro = mensagemDeErro(e))
            }
        }

        viewModelScope.launch {
            combine(
                processoRepository.observarPorId(processoId),
                historicoRepository.observarAtivoPorProcesso(processoId),
                faseRepository.observarTodas(),
                perfilRepository.observarTodos()
            ) { processo, historico, fases, perfis ->
                val faseAtual = processo?.let { p -> fases.find { it.id == p.faseAtualId } }
                val diasParado = historico?.let { ChronoUnit.DAYS.between(it.dataEntrada, LocalDate.now()) } ?: 0L
                val semaforo = faseAtual?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                    ?: StatusSemaforo.OK
                val sessao = SupabaseSessionManager.perfilAtual.value
                val podeEditar = processo != null && sessao != null &&
                    podeEditarProcesso(sessao.papel, processo.responsavelId, sessao.id)
                Sextupla(processo, faseAtual, historico, semaforo, fases to perfis, podeEditar)
            }.collect { (processo, faseAtual, historico, semaforo, fasesPerfis, podeEditar) ->
                val estadoAtual = _uiState.value
                _uiState.value = estadoAtual.copy(
                    carregando = false,
                    processo = processo,
                    faseAtual = faseAtual,
                    historicoAtual = historico,
                    statusSemaforo = semaforo,
                    fases = fasesPerfis.first,
                    perfis = fasesPerfis.second,
                    observacao = if (!sementeAplicada && historico != null) historico.observacoes else estadoAtual.observacao,
                    executorId = if (!sementeAplicada && historico != null) historico.responsavelId else estadoAtual.executorId,
                    prazoLimite = if (!sementeAplicada && historico != null) historico.prazoLimite else estadoAtual.prazoLimite,
                    notificarPrazo = if (!sementeAplicada && historico != null) historico.notificarPrazo else estadoAtual.notificarPrazo,
                    podeEditar = podeEditar
                )
                if (historico != null) {
                    sementeAplicada = true
                    if (historicoSincronizado != historico.id) {
                        historicoSincronizado = historico.id
                        diligenciaRepository.sincronizar(historico.id)
                    }
                }
            }
        }
    }

    fun atualizarObservacao(valor: String) {
        if (valor.length <= LIMITE_CARACTERES_OBSERVACAO) {
            _uiState.value = _uiState.value.copy(observacao = valor)
        }
    }

    fun atualizarExecutor(perfilId: String?) {
        _uiState.value = _uiState.value.copy(executorId = perfilId)
    }

    fun atualizarPrazoLimite(data: LocalDate?) {
        _uiState.value = _uiState.value.copy(prazoLimite = data)
    }

    fun atualizarNotificarPrazo(valor: Boolean) {
        _uiState.value = _uiState.value.copy(notificarPrazo = valor)
    }

    fun salvarEntradaAtual() {
        val estado = _uiState.value
        if (!estado.podeEditar) return
        val historico = estado.historicoAtual ?: return
        viewModelScope.launch {
            limparErro()
            try {
                historicoRepository.salvarEntradaAtual(
                    historico = historico,
                    responsavelId = estado.executorId,
                    prazoLimite = estado.prazoLimite,
                    notificarPrazo = estado.notificarPrazo,
                    novaObservacao = estado.observacao
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                registrarErro(e)
            }
        }
    }

    fun mudarFase(faseDestinoId: String, motivoRetorno: String?, onConcluido: () -> Unit) {
        val estado = _uiState.value
        if (!estado.podeEditar) return
        val processo = estado.processo ?: return
        viewModelScope.launch {
            limparErro()
            try {
                // avancar_fase() só recebe p_observacao_inicial da fase NOVA — a
                // observação pendente da fase atual (digitada mas nunca salva
                // via o ícone "Salvar") precisa ser gravada antes de fechar essa
                // entrada, ou o RPC a descarta silenciosamente.
                estado.historicoAtual?.let { historico ->
                    historicoRepository.salvarEntradaAtual(
                        historico = historico,
                        responsavelId = estado.executorId,
                        prazoLimite = estado.prazoLimite,
                        notificarPrazo = estado.notificarPrazo,
                        novaObservacao = estado.observacao
                    )
                }
                historicoRepository.mudarFase(
                    processo = processo,
                    faseDestinoId = faseDestinoId,
                    executorId = estado.executorId,
                    prazoLimite = estado.prazoLimite,
                    motivoRetorno = motivoRetorno,
                    notificarPrazo = estado.notificarPrazo
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                registrarErro(e)
                // Não navega de volta: o usuário precisa ver a mensagem.
                return@launch
            }
            onConcluido()
        }
    }

    fun registrarDiligencia(conteudo: String) {
        if (conteudo.isBlank()) return
        val historicoId = _uiState.value.historicoAtual?.id ?: return
        viewModelScope.launch {
            limparErro()
            try {
                diligenciaRepository.registrar(historicoId, conteudo)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                registrarErro(e)
            }
        }
    }

    private fun limparErro() {
        if (_uiState.value.erro != null) {
            _uiState.value = _uiState.value.copy(erro = null)
        }
    }

    private fun registrarErro(e: Exception) {
        _uiState.value = _uiState.value.copy(erro = mensagemDeErro(e))
    }
}

private data class Sextupla<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
