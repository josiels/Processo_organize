package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.ui.common.MENSAGEM_SESSAO_AUSENTE
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FaseCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FaseRepository(
        dao = AppDatabase.getInstance(application).faseDao(),
        client = SupabaseSessionManager.client
    )

    val fases: StateFlow<List<FaseEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _erro = MutableStateFlow<String?>(null)

    /** Falha da última escrita (rede ou rejeição do servidor), exibida na tela. */
    val erro: StateFlow<String?> = _erro.asStateFlow()

    fun salvar(
        id: String?,
        nome: String,
        ordem: Int,
        descricao: String?,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int
    ) {
        // Mesmo guard de TipoProcessoCadastroViewModel: "" numa coluna uuid é 400.
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
                    organizacaoId = organizacaoId,
                    nome = nome,
                    ordem = ordem,
                    descricao = descricao,
                    diasAlertaAtencao = diasAlertaAtencao,
                    diasAlertaCritico = diasAlertaCritico
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }

    fun excluir(fase: FaseEntity) {
        viewModelScope.launch {
            _erro.value = null
            try {
                repository.excluir(fase)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }
}
