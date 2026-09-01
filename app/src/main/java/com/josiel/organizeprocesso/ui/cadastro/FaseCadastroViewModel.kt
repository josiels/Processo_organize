package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FaseCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FaseRepository(
        dao = AppDatabase.getInstance(application).faseDao(),
        client = SupabaseSessionManager.client
    )

    val fases: StateFlow<List<FaseEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvar(
        id: String?,
        nome: String,
        ordem: Int,
        descricao: String?,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int
    ) {
        viewModelScope.launch {
            repository.salvar(
                id = id,
                organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty(),
                nome = nome,
                ordem = ordem,
                descricao = descricao,
                diasAlertaAtencao = diasAlertaAtencao,
                diasAlertaCritico = diasAlertaCritico
            )
        }
    }

    fun excluir(fase: FaseEntity) {
        viewModelScope.launch { repository.excluir(fase) }
    }
}
