package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TipoProcessoDao {
    @Upsert
    suspend fun upsert(tipoProcesso: TipoProcessoEntity)

    @Upsert
    suspend fun upsertTodos(tiposProcesso: List<TipoProcessoEntity>)

    @Delete
    suspend fun delete(tipoProcesso: TipoProcessoEntity)

    @Query("DELETE FROM tipos_processo")
    suspend fun limparTudo()

    @Query("SELECT * FROM tipos_processo WHERE id = :id")
    fun observarPorId(id: String): Flow<TipoProcessoEntity?>

    @Query("SELECT * FROM tipos_processo ORDER BY nome")
    fun observarTodas(): Flow<List<TipoProcessoEntity>>
}
