package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.PessoaDao
import com.josiel.organizeprocesso.data.local.PessoaEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Abstrai a origem dos dados de Pessoa — hoje só Room; Supabase entra na etapa de sincronização. */
class PessoaRepository(
    private val dao: PessoaDao,
    private val deviceId: String
) {
    fun observarTodas(): Flow<List<PessoaEntity>> = dao.observarTodas()

    suspend fun salvar(id: String?, nome: String, cargoSetor: String?, ativo: Boolean) {
        dao.upsert(
            PessoaEntity(
                id = id ?: UUID.randomUUID().toString(),
                nome = nome,
                cargoSetor = cargoSetor?.takeIf { it.isNotBlank() },
                ativo = ativo,
                updatedAt = Instant.now(),
                synced = false,
                deviceOrigin = deviceId
            )
        )
    }

    suspend fun excluir(pessoa: PessoaEntity) = dao.delete(pessoa)
}
