package com.josiel.organizeprocesso.ui.equipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.domain.model.Papel
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CriarContaUiState(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val papel: Papel = Papel.USUARIO,
    val salvando: Boolean = false,
    val erro: String? = null
) {
    val valido: Boolean
        get() = nome.isNotBlank() && email.isNotBlank() && senha.length >= 6
}

/** Formulário de criação de conta (spec do Plano 2C, seção 3) — chama a Edge Function `criar-conta`. */
class CriarContaViewModel(application: Application) : AndroidViewModel(application) {
    private val perfilRepository = PerfilRepository(
        dao = AppDatabase.getInstance(application).perfilDao(),
        client = SupabaseSessionManager.client
    )

    private val _uiState = MutableStateFlow(CriarContaUiState())
    val uiState: StateFlow<CriarContaUiState> = _uiState.asStateFlow()

    fun atualizarNome(valor: String) { _uiState.value = _uiState.value.copy(nome = valor) }
    fun atualizarEmail(valor: String) { _uiState.value = _uiState.value.copy(email = valor) }
    fun atualizarSenha(valor: String) { _uiState.value = _uiState.value.copy(senha = valor) }
    fun atualizarPapel(valor: Papel) { _uiState.value = _uiState.value.copy(papel = valor) }

    fun criar(onCriado: () -> Unit) {
        val estado = _uiState.value
        if (!estado.valido || estado.salvando) return
        _uiState.value = estado.copy(salvando = true, erro = null)
        viewModelScope.launch {
            try {
                SupabaseSessionManager.client.functions.invoke(
                    "criar-conta",
                    body = buildJsonObject {
                        put("nome", estado.nome.trim())
                        put("email", estado.email.trim())
                        put("senha", estado.senha)
                        put("papel", estado.papel.name.lowercase())
                    }
                )
                perfilRepository.sincronizar()
                _uiState.value = _uiState.value.copy(salvando = false)
                onCriado()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(salvando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
