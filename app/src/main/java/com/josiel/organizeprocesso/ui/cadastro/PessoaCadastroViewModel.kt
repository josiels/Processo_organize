package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.PessoaEntity
import com.josiel.organizeprocesso.data.repository.PessoaRepository
import com.josiel.organizeprocesso.data.util.DeviceId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PessoaCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PessoaRepository(
        dao = AppDatabase.getInstance(application).pessoaDao(),
        deviceId = DeviceId.obter(application)
    )

    val pessoas: StateFlow<List<PessoaEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvar(id: String?, nome: String, cargoSetor: String?, ativo: Boolean) {
        viewModelScope.launch { repository.salvar(id, nome, cargoSetor, ativo) }
    }

    fun excluir(pessoa: PessoaEntity) {
        viewModelScope.launch { repository.excluir(pessoa) }
    }
}
