package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("SELECT * FROM itens WHERE processoId = :processoId")
    fun observarPorProcesso(processoId: String): Flow<List<ItemEntity>>
}
