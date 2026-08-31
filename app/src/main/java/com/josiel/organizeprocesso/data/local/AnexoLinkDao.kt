package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnexoLinkDao {
    @Upsert
    suspend fun upsert(anexo: AnexoLinkEntity)

    @Delete
    suspend fun delete(anexo: AnexoLinkEntity)

    @Query("SELECT * FROM anexos_links WHERE processoFaseHistoricoId = :processoFaseHistoricoId")
    fun observarPorHistorico(processoFaseHistoricoId: String): Flow<List<AnexoLinkEntity>>
}
