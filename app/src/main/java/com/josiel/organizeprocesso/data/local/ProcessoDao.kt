package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessoDao {
    @Upsert
    suspend fun upsert(processo: ProcessoEntity)

    @Delete
    suspend fun delete(processo: ProcessoEntity)

    @Query("SELECT * FROM processos WHERE id = :id")
    fun observarPorId(id: String): Flow<ProcessoEntity?>

    @Query("SELECT * FROM processos ORDER BY dataAbertura DESC")
    fun observarTodos(): Flow<List<ProcessoEntity>>
}
