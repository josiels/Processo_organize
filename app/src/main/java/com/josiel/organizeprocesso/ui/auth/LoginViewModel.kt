package com.josiel.organizeprocesso.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoginUiState(
    val carregando: Boolean = false,
    val erro: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(carregando = true)
            try {
                SupabaseSessionManager.login(email, senha)
                _uiState.value = LoginUiState(carregando = false)
            } catch (e: Exception) {
                _uiState.value = LoginUiState(carregando = false, erro = "Não foi possível entrar. Verifique e-mail e senha.")
            }
        }
    }
}
