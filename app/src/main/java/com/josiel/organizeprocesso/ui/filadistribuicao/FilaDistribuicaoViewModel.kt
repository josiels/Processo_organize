package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FilaDistribuicaoRepository
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilaDistribuicaoUiState(
    val carregando: Boolean = true,
    val itens: List<FilaDistribuicaoItem> = emptyList(),
    val erro: String? = null
)

/** Fila de Distribuição (spec do Plano 2C, seção 4) — consulta ao vivo, sem cache Room. */
class FilaDistribuicaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FilaDistribuicaoRepository(SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(FilaDistribuicaoUiState())
    val uiState: StateFlow<FilaDistribuicaoUiState> = _uiState.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null)
            try {
                val itens = repository.listar()
                _uiState.value = _uiState.value.copy(carregando = false, itens = itens)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(carregando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
