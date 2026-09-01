package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.ItemDao
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.remote.dto.ItemDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest; Room é cache de leitura por processo. */
class ItemRepository(
    private val dao: ItemDao,
    private val client: SupabaseClient
) {
    fun observarPorProcesso(processoId: String): Flow<List<ItemEntity>> = dao.observarPorProcesso(processoId)

    suspend fun sincronizar(processoId: String) {
        val dtos = client.postgrest["itens"].select {
            filter { eq("processo_id", processoId) }
        }.decodeList<ItemDto>()
        dtos.forEach { dao.upsert(it.paraEntity(processoId)) }
    }

    suspend fun salvar(item: ItemEntity) {
        val linha = buildJsonObject {
            put("id", item.id)
            put("processo_id", item.processoId)
            put("descricao", item.descricao)
            put("quantidade", item.quantidade)
            put("unidade", item.unidade)
            put("valor_estimado_unit", item.valorEstimadoUnit)
            // Sempre inclui a chave, mesmo quando null: upsert do Postgrest só
            // toca colunas presentes no payload — omitir a chave deixaria um
            // valor antigo intacto no servidor em vez de limpá-lo (mesmo bug
            // corrigido em ProcessoRepository.atualizar(), Task 5).
            put("valor_pesquisa_unit", item.valorPesquisaUnit)
        }
        client.postgrest["itens"].upsert(linha)
        sincronizar(item.processoId)
    }

    suspend fun excluir(item: ItemEntity) {
        client.postgrest["itens"].delete {
            filter { eq("id", item.id) }
        }
        dao.delete(item)
    }
}
