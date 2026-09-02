package com.josiel.organizeprocesso.ui.configuracoes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.ui.common.MENSAGEM_SESSAO_AUSENTE
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ConfiguracoesUiState(
    val carregando: Boolean = true,
    val notificarAvancoFase: Boolean = true,
    val notificarPrazo: Boolean = true,
    val notificarTempoParado: Boolean = true,
    val erro: String? = null
)

/** Configurações pessoais de notificação (spec do Plano 2C, seção 5). */
class ConfiguracoesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(ConfiguracoesUiState())
    val uiState: StateFlow<ConfiguracoesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val meuId = SupabaseSessionManager.perfilAtual.value?.id
            val perfil = perfilRepository.observarTodos().first().find { it.id == meuId }
            _uiState.value = if (perfil != null) {
                ConfiguracoesUiState(
                    carregando = false,
                    notificarAvancoFase = perfil.notificarAvancoFase,
                    notificarPrazo = perfil.notificarPrazo,
                    notificarTempoParado = perfil.notificarTempoParado
                )
            } else {
                _uiState.value.copy(carregando = false)
            }
        }
    }

    fun atualizarNotificarAvancoFase(valor: Boolean) = salvar(_uiState.value.copy(notificarAvancoFase = valor))
    fun atualizarNotificarPrazo(valor: Boolean) = salvar(_uiState.value.copy(notificarPrazo = valor))
    fun atualizarNotificarTempoParado(valor: Boolean) = salvar(_uiState.value.copy(notificarTempoParado = valor))

    private fun salvar(novoEstado: ConfiguracoesUiState) {
        val perfilId = SupabaseSessionManager.perfilAtual.value?.id
        if (perfilId == null) {
            _uiState.value = novoEstado.copy(erro = MENSAGEM_SESSAO_AUSENTE)
            return
        }
        _uiState.value = novoEstado.copy(erro = null)
        viewModelScope.launch {
            try {
                perfilRepository.atualizarPreferenciasNotificacao(
                    perfilId = perfilId,
                    notificarAvancoFase = novoEstado.notificarAvancoFase,
                    notificarPrazo = novoEstado.notificarPrazo,
                    notificarTempoParado = novoEstado.notificarTempoParado
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(erro = mensagemDeErro(e))
            }
        }
    }
}
