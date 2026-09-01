package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TipoProcessoCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TipoProcessoRepository(
        dao = AppDatabase.getInstance(application).tipoProcessoDao(),
        client = SupabaseSessionManager.client
    )

    val tiposProcesso: StateFlow<List<TipoProcessoEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvar(id: String?, nome: String, diasAlertaAtencao: Int, diasAlertaCritico: Int) {
        viewModelScope.launch {
            repository.salvar(
                id = id,
                nome = nome,
                diasAlertaAtencao = diasAlertaAtencao,
                diasAlertaCritico = diasAlertaCritico,
                organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty()
            )
        }
    }

    fun excluir(tipoProcesso: TipoProcessoEntity) {
        viewModelScope.launch { repository.excluir(tipoProcesso) }
    }
}
