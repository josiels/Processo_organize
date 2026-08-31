package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.FaseDao
import com.josiel.organizeprocesso.data.local.FaseEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Abstrai a origem dos dados de Fase — hoje só Room; Supabase entra na etapa de sincronização. */
class FaseRepository(
    private val dao: FaseDao,
    private val deviceId: String
) {
    fun observarTodas(): Flow<List<FaseEntity>> = dao.observarTodas()

    suspend fun salvar(
        id: String?,
        nome: String,
        ordem: Int,
        descricao: String?,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int,
        padrao: Boolean
    ) {
        dao.upsert(
            FaseEntity(
                id = id ?: UUID.randomUUID().toString(),
                nome = nome,
                ordem = ordem,
                descricao = descricao?.takeIf { it.isNotBlank() },
                diasAlertaAtencao = diasAlertaAtencao,
                diasAlertaCritico = diasAlertaCritico,
                padrao = padrao,
                updatedAt = Instant.now(),
                synced = false,
                deviceOrigin = deviceId
            )
        )
    }

    suspend fun excluir(fase: FaseEntity) = dao.delete(fase)
}
