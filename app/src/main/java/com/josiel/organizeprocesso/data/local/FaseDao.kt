package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FaseDao {
    @Upsert
    suspend fun upsert(fase: FaseEntity)

    @Upsert
    suspend fun upsertTodos(fases: List<FaseEntity>)

    @Delete
    suspend fun delete(fase: FaseEntity)

    @Query("SELECT * FROM fases WHERE id = :id")
    fun observarPorId(id: String): Flow<FaseEntity?>

    @Query("SELECT * FROM fases ORDER BY ordem")
    fun observarTodas(): Flow<List<FaseEntity>>
}
