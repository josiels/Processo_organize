package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.ItemDao
import com.josiel.organizeprocesso.data.local.ItemEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Abstrai a origem dos dados de Item — hoje só Room; Supabase entra na etapa de sincronização. */
class ItemRepository(
    private val dao: ItemDao,
    private val deviceId: String
) {
    fun observarPorProcesso(processoId: String): Flow<List<ItemEntity>> = dao.observarPorProcesso(processoId)

    suspend fun salvar(item: ItemEntity) {
        dao.upsert(item.copy(updatedAt = Instant.now(), synced = false, deviceOrigin = deviceId))
    }

    suspend fun excluir(item: ItemEntity) = dao.delete(item)
}
