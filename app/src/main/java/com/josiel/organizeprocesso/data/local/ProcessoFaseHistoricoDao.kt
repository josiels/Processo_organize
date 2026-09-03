package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessoFaseHistoricoDao {
    @Upsert
    suspend fun upsert(historico: ProcessoFaseHistoricoEntity)

    @Delete
    suspend fun delete(historico: ProcessoFaseHistoricoEntity)

    @Query("SELECT * FROM processo_fase_historico WHERE id = :id")
    fun observarPorId(id: String): Flow<ProcessoFaseHistoricoEntity?>

    @Query("SELECT * FROM processo_fase_historico WHERE processoId = :processoId ORDER BY dataEntrada")
    fun observarPorProcesso(processoId: String): Flow<List<ProcessoFaseHistoricoEntity>>

    /** Uma linha por processo: a fase corrente de cada um (usado na lista de Processos). */
    @Query("SELECT * FROM processo_fase_historico WHERE dataSaida IS NULL")
    fun observarTodosAtivos(): Flow<List<ProcessoFaseHistoricoEntity>>

    /** Toda a tabela (a RLS já limita à organização) — usado pelo Dashboard para achar avanços de fase de hoje, mesmo em entradas já fechadas. */
    @Query("SELECT * FROM processo_fase_historico")
    fun observarTodos(): Flow<List<ProcessoFaseHistoricoEntity>>

    /** A entrada de histórico da fase corrente de UM processo (usado na tela Avançar Fase). */
    @Query("SELECT * FROM processo_fase_historico WHERE processoId = :processoId AND dataSaida IS NULL LIMIT 1")
    fun observarAtivoPorProcesso(processoId: String): Flow<ProcessoFaseHistoricoEntity?>
}
