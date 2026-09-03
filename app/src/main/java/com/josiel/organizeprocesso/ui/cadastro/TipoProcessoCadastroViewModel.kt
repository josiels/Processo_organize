package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.ui.common.MENSAGEM_SESSAO_AUSENTE
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TipoProcessoCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TipoProcessoRepository(
        dao = AppDatabase.getInstance(application).tipoProcessoDao(),
        client = SupabaseSessionManager.client
    )
    private val faseRepository = FaseRepository(
        dao = AppDatabase.getInstance(application).faseDao(),
        client = SupabaseSessionManager.client
    )

    val tiposProcesso: StateFlow<List<TipoProcessoEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catálogo de Fases da organização, para o seletor de "fase única" de um tipo simples. */
    val fases: StateFlow<List<FaseEntity>> = faseRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _erro = MutableStateFlow<String?>(null)

    /** Falha da última escrita (rede ou rejeição do servidor), exibida na tela. */
    val erro: StateFlow<String?> = _erro.asStateFlow()

    fun salvar(
        id: String?,
        nome: String,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int,
        simples: Boolean,
        fasePadraoId: String?
    ) {
        // Sem perfil carregado o organizacao_id iria vazio numa coluna uuid e o
        // servidor devolveria 400 — nem tenta.
        val organizacaoId = SupabaseSessionManager.perfilAtual.value?.organizacaoId
        if (organizacaoId.isNullOrBlank()) {
            _erro.value = MENSAGEM_SESSAO_AUSENTE
            return
        }
        viewModelScope.launch {
            _erro.value = null
            try {
                repository.salvar(
                    id = id,
                    nome = nome,
                    diasAlertaAtencao = diasAlertaAtencao,
                    diasAlertaCritico = diasAlertaCritico,
                    simples = simples,
                    fasePadraoId = fasePadraoId,
                    organizacaoId = organizacaoId
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }

    fun excluir(tipoProcesso: TipoProcessoEntity) {
        viewModelScope.launch {
            _erro.value = null
            try {
                repository.excluir(tipoProcesso)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }
}
