package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoPorTipoDto
import com.josiel.organizeprocesso.data.repository.FilaDistribuicaoRepository
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilaDistribuicaoDetalheUiState(
    val carregando: Boolean = true,
    val itens: List<FilaDistribuicaoPorTipoDto> = emptyList(),
    val erro: String? = null
)

class FilaDistribuicaoDetalheViewModel(
    application: Application,
    private val perfilId: String
) : AndroidViewModel(application) {
    private val repository = FilaDistribuicaoRepository(SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(FilaDistribuicaoDetalheUiState())
    val uiState: StateFlow<FilaDistribuicaoDetalheUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val itens = repository.listarPorTipo(perfilId)
                _uiState.value = _uiState.value.copy(carregando = false, itens = itens)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(carregando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
