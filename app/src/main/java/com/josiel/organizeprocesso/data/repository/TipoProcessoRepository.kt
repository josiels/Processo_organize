package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.TipoProcessoDao
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.dto.TipoProcessoDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest (admin-only no backend); Room é só cache de leitura. */
class TipoProcessoRepository(
    private val dao: TipoProcessoDao,
    private val client: SupabaseClient
) {
    fun observarTodas(): Flow<List<TipoProcessoEntity>> = dao.observarTodas()

    suspend fun sincronizar() {
        val dtos = client.postgrest["tipos_processo"].select().decodeList<TipoProcessoDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }

    suspend fun salvar(
        id: String?,
        nome: String,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int,
        organizacaoId: String,
        // Defaults preservam a chamada existente em TipoProcessoCadastroViewModel
        // até a Task 3 atualizá-la para passar os dois explicitamente — sem
        // isso, o build ficaria quebrado entre a Task 2 e a Task 3.
        simples: Boolean = false,
        fasePadraoId: String? = null
    ) {
        val linha = buildJsonObject {
            put("id", id ?: UUID.randomUUID().toString())
            put("organizacao_id", organizacaoId)
            put("nome", nome)
            put("dias_alerta_atencao", diasAlertaAtencao)
            put("dias_alerta_critico", diasAlertaCritico)
            put("simples", simples)
            // Sempre inclui a chave, mesmo quando null: upsert do Postgrest só
            // toca colunas presentes no payload — omitir a chave deixaria uma
            // fase_padrao_id antiga intacta no servidor ao desligar o toggle
            // "simples" na edição de um tipo (mesmo motivo documentado em
            // ProcessoRepository.atualizar() para valor_pesquisa_unit).
            put("fase_padrao_id", fasePadraoId)
        }
        client.postgrest["tipos_processo"].upsert(linha)
        sincronizar()
    }

    suspend fun excluir(tipoProcesso: TipoProcessoEntity) {
        client.postgrest["tipos_processo"].delete {
            filter { eq("id", tipoProcesso.id) }
        }
        dao.delete(tipoProcesso)
    }
}

private fun TipoProcessoDto.paraEntity(): TipoProcessoEntity = TipoProcessoEntity(
    id = id,
    organizacaoId = organizacaoId,
    nome = nome,
    diasAlertaAtencao = diasAlertaAtencao,
    diasAlertaCritico = diasAlertaCritico,
    simples = simples,
    fasePadraoId = fasePadraoId
)
