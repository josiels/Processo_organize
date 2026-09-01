package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.DiligenciaDao
import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import com.josiel.organizeprocesso.data.remote.dto.DiligenciaDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Append-only (spec do Plano 2B, seção 3.2) — não existe atualizar()/excluir(). */
class DiligenciaRepository(
    private val dao: DiligenciaDao,
    private val client: SupabaseClient
) {
    fun observarPorHistorico(historicoId: String): Flow<List<DiligenciaEntity>> =
        dao.observarPorHistorico(historicoId)

    suspend fun sincronizar(historicoId: String) {
        val dtos = client.postgrest["diligencias"].select {
            filter { eq("processo_fase_historico_id", historicoId) }
        }.decodeList<DiligenciaDto>()
        dao.upsertTodas(dtos.map { it.paraEntity() })
    }

    suspend fun registrar(historicoId: String, conteudo: String) {
        val linha = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("processo_fase_historico_id", historicoId)
            put("conteudo", conteudo)
        }
        client.postgrest["diligencias"].insert(linha)
        sincronizar(historicoId)
    }
}
