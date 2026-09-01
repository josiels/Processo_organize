package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.HistoricoFaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val pessoas: List<PerfilEntity> = emptyList(),
    val observacao: String = "",
    val responsavelId: String? = null,
    val prazoLimite: LocalDate? = null,
    val notificarPrazo: Boolean = false
)

/** ViewModel da tela Avançar/Retroceder Fase (REQUISITOS.md, seção 9; ROADMAP.md, passo 9). */
class AvancarFaseViewModel(
    application: Application,
    private val processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoRepository = HistoricoFaseRepository(database)

    private val _uiState = MutableStateFlow(AvancarFaseUiState())
    val uiState: StateFlow<AvancarFaseUiState> = _uiState.asStateFlow()

    private var sementeAplicada = false

    init {
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
                Quintuplo(processo, faseAtual, historico, semaforo, fases to perfis)
            }.collect { (processo, faseAtual, historico, semaforo, fasesPessoas) ->
                val estadoAtual = _uiState.value
                _uiState.value = estadoAtual.copy(
                    carregando = false,
                    processo = processo,
                    faseAtual = faseAtual,
                    historicoAtual = historico,
                    statusSemaforo = semaforo,
                    fases = fasesPessoas.first,
                    pessoas = fasesPessoas.second,
                    observacao = if (!sementeAplicada && historico != null) historico.observacoes else estadoAtual.observacao,
                    responsavelId = if (!sementeAplicada && historico != null) historico.responsavelId else estadoAtual.responsavelId,
                    prazoLimite = if (!sementeAplicada && historico != null) historico.prazoLimite else estadoAtual.prazoLimite,
                    notificarPrazo = if (!sementeAplicada && historico != null) historico.notificarPrazo else estadoAtual.notificarPrazo
                )
                if (historico != null) sementeAplicada = true
            }
        }
    }

    fun atualizarObservacao(valor: String) {
        if (valor.length <= LIMITE_CARACTERES_OBSERVACAO) {
            _uiState.value = _uiState.value.copy(observacao = valor)
        }
    }

    fun atualizarResponsavel(pessoaId: String?) {
        _uiState.value = _uiState.value.copy(responsavelId = pessoaId)
    }

    fun atualizarPrazoLimite(data: LocalDate?) {
        _uiState.value = _uiState.value.copy(prazoLimite = data)
    }

    fun atualizarNotificarPrazo(valor: Boolean) {
        _uiState.value = _uiState.value.copy(notificarPrazo = valor)
    }

    fun salvarEntradaAtual() {
        val estado = _uiState.value
        val historico = estado.historicoAtual ?: return
        viewModelScope.launch {
            historicoRepository.salvarEntradaAtual(
                historico = historico,
                responsavelId = estado.responsavelId,
                prazoLimite = estado.prazoLimite,
                notificarPrazo = estado.notificarPrazo,
                novaObservacao = estado.observacao
            )
        }
    }

    fun mudarFase(faseDestinoId: String, dataEntrada: LocalDate, motivoRetorno: String?, onConcluido: () -> Unit) {
        val estado = _uiState.value
        val processo = estado.processo ?: return
        val historico = estado.historicoAtual ?: return
        viewModelScope.launch {
            historicoRepository.mudarFase(
                processo = processo,
                historicoAtual = historico,
                faseDestinoId = faseDestinoId,
                dataEntrada = dataEntrada,
                responsavelId = estado.responsavelId,
                prazoLimite = estado.prazoLimite,
                motivoRetorno = motivoRetorno,
                notificarPrazo = estado.notificarPrazo
            )
            onConcluido()
        }
    }
}

private data class Quintuplo<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
