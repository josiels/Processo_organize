package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.remote.dto.ItemDto
import com.josiel.organizeprocesso.data.remote.dto.ProcessoDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import com.josiel.organizeprocesso.data.remote.updateVerificado
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Escreve direto no Postgrest (RLS: só admin cria; admin/dono/órfão edita).
 * Room é cache de leitura, atualizado após a confirmação do servidor — não
 * há mais fila de sincronização local (spec do pivô, seção 10).
 */
class ProcessoRepository(
    private val database: AppDatabase,
    private val client: SupabaseClient
) {
    private val processoDao = database.processoDao()
    private val itemDao = database.itemDao()

    fun observarTodos(): Flow<List<ProcessoEntity>> = processoDao.observarTodos()
    fun observarPorId(id: String): Flow<ProcessoEntity?> = processoDao.observarPorId(id)
    fun observarItens(processoId: String): Flow<List<ItemEntity>> = itemDao.observarPorProcesso(processoId)

    suspend fun sincronizar() {
        val dtos = client.postgrest["processos"].select().decodeList<ProcessoDto>()
        dtos.forEach { processoDao.upsert(it.paraEntity()) }
    }

    suspend fun criar(
        organizacaoId: String,
        numero: String,
        objeto: String,
        descricao: String,
        orgaoDemandante: String,
        tipoProcessoId: String,
        dataAbertura: LocalDate,
        faseInicialId: String,
        statusGeral: StatusGeralProcesso,
        itens: List<ItemEntity>
    ): String {
        val processoId = UUID.randomUUID().toString()
        val valorTotal = itens.sumOf { it.quantidade * it.valorEstimadoUnit }

        val linhaProcesso = buildJsonObject {
            put("id", processoId)
            put("organizacao_id", organizacaoId)
            put("numero", numero)
            put("objeto", objeto)
            put("descricao", descricao)
            put("orgao_demandante", orgaoDemandante)
            put("tipo_processo_id", tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("data_abertura", dataAbertura.toString())
            put("fase_atual_id", faseInicialId)
            put("status_geral", statusGeral.name.lowercase())
        }
        client.postgrest["processos"].insert(linhaProcesso)

        // Todo processo corrente precisa de uma linha de processo_fase_historico
        // (ARQUITETURA.md, seção 4) — sem isso, AvancarFaseScreen fica presa em
        // "carregando" para sempre (depende de historicoAtual != null), a aba
        // Timeline nunca aparece, e o semáforo de fase não tem data de entrada
        // para comparar. Faltava desde a reescrita para Postgrest do Plano 2A —
        // real bug encontrado durante o levantamento do Plano 2D, corrigido aqui
        // porque afeta todo processo criado, não só o que 2D vai tocar.
        val linhaHistorico = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("processo_id", processoId)
            put("fase_id", faseInicialId)
            put("data_entrada", dataAbertura.toString())
            put("observacoes", "")
            put("notificar_prazo", false)
        }
        client.postgrest["processo_fase_historico"].insert(linhaHistorico)

        if (itens.isNotEmpty()) {
            val linhasItens = itens.map { item ->
                buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("processo_id", processoId)
                    put("descricao", item.descricao)
                    put("quantidade", item.quantidade)
                    put("unidade", item.unidade)
                    put("valor_estimado_unit", item.valorEstimadoUnit)
                    item.valorPesquisaUnit?.let { put("valor_pesquisa_unit", it) }
                }
            }
            client.postgrest["itens"].insert(linhasItens)
        }

        sincronizar()
        val itensCriados = client.postgrest["itens"].select {
            filter { eq("processo_id", processoId) }
        }.decodeList<ItemDto>()
        itensCriados.forEach { itemDao.upsert(it.paraEntity(processoId)) }
        return processoId
    }

    suspend fun atualizar(
        processo: ProcessoEntity,
        itensAtuais: List<ItemEntity>,
        itensRemovidos: List<ItemEntity>
    ) {
        val valorTotal = itensAtuais.sumOf { it.quantidade * it.valorEstimadoUnit }

        val linhaProcesso = buildJsonObject {
            put("numero", processo.numero)
            put("objeto", processo.objeto)
            put("descricao", processo.descricao)
            put("orgao_demandante", processo.orgaoDemandante)
            put("tipo_processo_id", processo.tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("status_geral", processo.statusGeral.name.lowercase())
            // Sem isto, uma edição direta de campo ou a conclusão de um
            // processo simples (ProcessoDetalheViewModel.concluir(), que
            // chama este método) ficariam invisíveis para o resumo de
            // "atualizações recentes" do Dashboard — só as RPCs avancar_fase/
            // designar_processo tocavam esta coluna até agora (spec do
            // Dashboard Início, seção 4).
            put("atualizado_em", Instant.now().toString())
        }
        client.postgrest["processos"].updateVerificado(linhaProcesso) {
            filter { eq("id", processo.id) }
        }

        itensAtuais.forEach { item ->
            val linhaItem = buildJsonObject {
                put("id", item.id)
                put("processo_id", processo.id)
                put("descricao", item.descricao)
                put("quantidade", item.quantidade)
                put("unidade", item.unidade)
                put("valor_estimado_unit", item.valorEstimadoUnit)
                // Sempre inclui a chave, mesmo quando null: upsert do Postgrest só
                // toca colunas presentes no payload — omitir a chave deixaria um
                // valor antigo intacto no servidor em vez de limpá-lo (bug real
                // encontrado em revisão: usuário zera valorPesquisaUnit, o campo
                // ausente não limpa a coluna, e a reconciliação abaixo reescreve o
                // valor obsoleto de volta no cache local).
                put("valor_pesquisa_unit", item.valorPesquisaUnit)
            }
            client.postgrest["itens"].upsert(linhaItem)
        }
        itensRemovidos.forEach { item ->
            client.postgrest["itens"].delete {
                filter { eq("id", item.id) }
            }
        }

        sincronizar()
        val itensAtualizados = client.postgrest["itens"].select {
            filter { eq("processo_id", processo.id) }
        }.decodeList<ItemDto>()
        itensAtualizados.forEach { itemDao.upsert(it.paraEntity(processo.id)) }
        itensRemovidos.forEach { itemDao.delete(it) }
    }

    /** Chama a RPC `designar_processo` — admin designa a qualquer um, ou o próprio usuário autoatribui/devolve um órfão (RLS do backend valida). */
    suspend fun designar(processoId: String, novoResponsavelId: String?) {
        client.postgrest.rpc(
            "designar_processo",
            buildJsonObject {
                put("p_processo_id", processoId)
                put("p_novo_responsavel_id", novoResponsavelId)
            }
        )
        sincronizar()
    }
}

fun ProcessoDto.paraEntity(): ProcessoEntity = ProcessoEntity(
    id = id,
    organizacaoId = organizacaoId,
    numero = numero,
    objeto = objeto,
    descricao = descricao,
    orgaoDemandante = orgaoDemandante,
    tipoProcessoId = tipoProcessoId,
    valorEstimadoTotal = valorEstimadoTotal,
    dataAbertura = LocalDate.parse(dataAbertura),
    faseAtualId = faseAtualId,
    statusGeral = StatusGeralProcesso.valueOf(statusGeral.uppercase()),
    responsavelId = responsavelId,
    designadoEm = designadoEm?.let(Instant::parse),
    designadoPor = designadoPor,
    criadoEm = Instant.parse(criadoEm),
    atualizadoEm = Instant.parse(atualizadoEm)
)
