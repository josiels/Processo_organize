package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiligenciaDao {
    @Upsert
    suspend fun upsertTodas(diligencias: List<DiligenciaEntity>)

    @Query("SELECT * FROM diligencias WHERE processoFaseHistoricoId = :historicoId ORDER BY criadoEm DESC")
    fun observarPorHistorico(historicoId: String): Flow<List<DiligenciaEntity>>
}
