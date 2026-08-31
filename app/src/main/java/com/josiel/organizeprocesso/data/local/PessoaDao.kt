package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PessoaDao {
    @Upsert
    suspend fun upsert(pessoa: PessoaEntity)

    @Delete
    suspend fun delete(pessoa: PessoaEntity)

    @Query("SELECT * FROM pessoas WHERE id = :id")
    fun observarPorId(id: String): Flow<PessoaEntity?>

    @Query("SELECT * FROM pessoas ORDER BY nome")
    fun observarTodas(): Flow<List<PessoaEntity>>
}
