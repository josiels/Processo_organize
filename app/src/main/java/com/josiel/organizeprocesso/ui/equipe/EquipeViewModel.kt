package com.josiel.organizeprocesso.ui.equipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Gerenciar equipe/contas (spec do Plano 2C, seção 3) — admin-only, só leitura. */
class EquipeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PerfilRepository(
        dao = AppDatabase.getInstance(application).perfilDao(),
        client = SupabaseSessionManager.client
    )

    val perfis: StateFlow<List<PerfilEntity>> = repository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
