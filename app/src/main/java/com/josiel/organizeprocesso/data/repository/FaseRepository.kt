package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.FaseDao
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.remote.dto.FaseDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest (admin-only no backend); Room é cache de leitura. */
class FaseRepository(
    private val dao: FaseDao,
    private val client: SupabaseClient
) {
    fun observarTodas(): Flow<List<FaseEntity>> = dao.observarTodas()

    suspend fun sincronizar() {
        val dtos = client.postgrest["fases"].select().decodeList<FaseDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }

    suspend fun salvar(
        id: String?,
        organizacaoId: String,
        nome: String,
        ordem: Int,
        descricao: String?,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int
    ) {
        val linha = buildJsonObject {
            put("id", id ?: UUID.randomUUID().toString())
            put("organizacao_id", organizacaoId)
            put("nome", nome)
            put("ordem", ordem)
            // Sempre inclui a chave, mesmo quando null: upsert do Postgrest só
            // toca colunas presentes no payload — omitir a chave deixaria um
            // valor antigo intacto no servidor em vez de limpá-lo (mesmo bug
            // corrigido em ProcessoRepository.atualizar(), Task 5, e em
            // ItemRepository.salvar(), Task 6).
            put("descricao", descricao?.takeIf { it.isNotBlank() })
            put("dias_alerta_atencao", diasAlertaAtencao)
            put("dias_alerta_critico", diasAlertaCritico)
        }
        client.postgrest["fases"].upsert(linha)
        sincronizar()
    }

    suspend fun excluir(fase: FaseEntity) {
        client.postgrest["fases"].delete {
            filter { eq("id", fase.id) }
        }
        dao.delete(fase)
    }
}
