package com.josiel.organizeprocesso.ui.configuracoes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.PerfilEntity
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

/** Mensagem exibida quando o perfil do usuário não pôde ser localizado, mesmo após tentar ressincronizar. */
private const val MENSAGEM_PREFERENCIAS_INDISPONIVEIS = "Não foi possível carregar suas preferências."

data class ConfiguracoesUiState(
    val carregando: Boolean = true,
    /**
     * true quando o perfil não pôde ser determinado (nem no cache local, nem
     * após ressincronizar) — a Screen deve mostrar apenas o erro e nenhum
     * toggle, para não permitir que o usuário aja sobre um valor default que
     * pode não corresponder ao que está salvo no servidor (finding I1+I2).
     */
    val bloqueado: Boolean = false,
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
            val perfil = buscarPerfilAtual(ressincronizarSeAusente = true)
            _uiState.value = if (perfil != null) {
                ConfiguracoesUiState(
                    carregando = false,
                    notificarAvancoFase = perfil.notificarAvancoFase,
                    notificarPrazo = perfil.notificarPrazo,
                    notificarTempoParado = perfil.notificarTempoParado
                )
            } else {
                ConfiguracoesUiState(carregando = false, bloqueado = true, erro = MENSAGEM_PREFERENCIAS_INDISPONIVEIS)
            }
        }
    }

    /**
     * Busca o perfil do usuário logado no cache Room local. Se ele não for
     * encontrado (cache `perfis` vazio por uma falha de sync anterior — ver
     * AppNavHost, cujo `LaunchedEffect(sessionStatus)` carrega `perfilAtual`
     * e sincroniza `perfis` em blocos try/catch separados que engolem falha
     * independentemente) e `ressincronizarSeAusente` for true, sincroniza uma
     * única vez com o servidor e tenta de novo. Nunca ressincroniza mais de
     * uma vez por chamada — evita retry infinito caso o perfil realmente não
     * exista (ex.: sessão inválida).
     */
    private suspend fun buscarPerfilAtual(ressincronizarSeAusente: Boolean): PerfilEntity? {
        val meuId = SupabaseSessionManager.perfilAtual.value?.id ?: return null
        val perfil = perfilRepository.observarTodos().first().find { it.id == meuId }
        if (perfil != null) return perfil
        if (!ressincronizarSeAusente) return null
        return try {
            perfilRepository.sincronizar()
            perfilRepository.observarTodos().first().find { it.id == meuId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
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
                // Não confia no estado otimista aplicado acima: relê do Room
                // (já ressincronizado dentro de atualizarPreferenciasNotificacao)
                // para refletir o valor real do servidor. Isso cobre o caso
                // de um update que combinou 0 linhas (perfilId obsoleto ou
                // uma borda de RLS) — o Postgrest responde 204 mesmo sem
                // alterar nada, então sem essa releitura a UI mostraria um
                // toggle como alterado quando o servidor manteve o valor
                // antigo (finding I1+I2).
                val perfilAtualizado = buscarPerfilAtual(ressincronizarSeAusente = false)
                _uiState.value = if (perfilAtualizado != null) {
                    _uiState.value.copy(
                        bloqueado = false,
                        notificarAvancoFase = perfilAtualizado.notificarAvancoFase,
                        notificarPrazo = perfilAtualizado.notificarPrazo,
                        notificarTempoParado = perfilAtualizado.notificarTempoParado
                    )
                } else {
                    _uiState.value.copy(bloqueado = true, erro = MENSAGEM_PREFERENCIAS_INDISPONIVEIS)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(erro = mensagemDeErro(e))
            }
        }
    }
}
