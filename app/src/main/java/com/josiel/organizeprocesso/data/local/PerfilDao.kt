package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Upsert
    suspend fun upsert(perfil: PerfilEntity)

    @Upsert
    suspend fun upsertTodos(perfis: List<PerfilEntity>)

    @Query("DELETE FROM perfis")
    suspend fun limparTudo()

    @Query("SELECT * FROM perfis WHERE id = :id")
    fun observarPorId(id: String): Flow<PerfilEntity?>

    @Query("SELECT * FROM perfis ORDER BY nome")
    fun observarTodos(): Flow<List<PerfilEntity>>
}
