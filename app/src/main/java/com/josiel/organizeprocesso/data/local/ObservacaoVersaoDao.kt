package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservacaoVersaoDao {
    @Upsert
    suspend fun upsert(versao: ObservacaoVersaoEntity)

    @Delete
    suspend fun delete(versao: ObservacaoVersaoEntity)

    @Query("SELECT * FROM observacao_versoes WHERE processoFaseHistoricoId = :processoFaseHistoricoId ORDER BY criadoEm")
    fun observarPorHistorico(processoFaseHistoricoId: String): Flow<List<ObservacaoVersaoEntity>>
}
