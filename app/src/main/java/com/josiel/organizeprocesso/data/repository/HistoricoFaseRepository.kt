package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ObservacaoVersaoEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.dto.ProcessoDto
import com.josiel.organizeprocesso.data.remote.dto.ProcessoFaseHistoricoDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Orquestra o histórico de fases de um processo — escreve direto no
 * Postgrest/RPC (spec do pivô, seção 10); Room é cache de leitura.
 */
class HistoricoFaseRepository(
    private val database: AppDatabase,
    private val client: SupabaseClient
) {
    private val processoDao = database.processoDao()
    private val historicoDao = database.processoFaseHistoricoDao()
    private val observacaoVersaoDao = database.observacaoVersaoDao()

    fun observarAtivoPorProcesso(processoId: String): Flow<ProcessoFaseHistoricoEntity?> =
        historicoDao.observarAtivoPorProcesso(processoId)

    fun observarVersoesObservacao(historicoId: String): Flow<List<ObservacaoVersaoEntity>> =
        observacaoVersaoDao.observarPorHistorico(historicoId)

    /**
     * Puxa o histórico de UM processo (usado ao abrir Detalhe/Avançar Fase e
     * depois de cada escrita). Público porque o cache local é destrutivo entre
     * versões do Room: sem esta chamada preguiçosa ao abrir a tela, a tabela
     * fica vazia para sempre e a tela de diligências nunca habilita.
     */
    suspend fun sincronizar(processoId: String) {
        val dtos = client.postgrest["processo_fase_historico"].select {
            filter { eq("processo_id", processoId) }
        }.decodeList<ProcessoFaseHistoricoDto>()
        dtos.forEach { historicoDao.upsert(it.paraEntity()) }
    }

    /**
     * Puxa a tabela inteira (a RLS já limita à organização do usuário) — mesmo
     * padrão "org pequena, tabela pequena" de `ProcessoRepository.sincronizar()`.
     * Necessário no sync pós-login porque o semáforo de fase da LISTA precisa do
     * histórico de todos os processos, não só do que está aberto na tela.
     * Depende de `processos`/`fases`/`perfis` já sincronizados (FKs do Room).
     */
    suspend fun sincronizarTodos() {
        val dtos = client.postgrest["processo_fase_historico"].select()
            .decodeList<ProcessoFaseHistoricoDto>()
        dtos.forEach { historicoDao.upsert(it.paraEntity()) }
    }

    /**
     * Relê a linha do processo no servidor. Depois de `avancar_fase()` o
     * servidor recalcula `fase_atual_id`/`atualizado_em`; adivinhar o novo
     * estado localmente deixaria o cache divergente (mesma reconciliação pós-
     * escrita que todos os outros repositórios já fazem).
     */
    private suspend fun sincronizarProcesso(processoId: String) {
        val dto = client.postgrest["processos"].select {
            filter { eq("id", processoId) }
        }.decodeSingle<ProcessoDto>()
        processoDao.upsert(dto.paraEntity())
    }

    /** Atualiza responsável/prazo/notificação/observação da entrada corrente, sem trocar de fase. */
    suspend fun salvarEntradaAtual(
        historico: ProcessoFaseHistoricoEntity,
        responsavelId: String?,
        prazoLimite: LocalDate?,
        notificarPrazo: Boolean,
        novaObservacao: String
    ) {
        val linha = buildJsonObject {
            put("responsavel_id", responsavelId)
            put("prazo_limite", prazoLimite?.toString())
            put("notificar_prazo", notificarPrazo)
            put("observacoes", novaObservacao)
        }
        client.postgrest["processo_fase_historico"].update(linha) {
            filter { eq("id", historico.id) }
        }
        if (novaObservacao != historico.observacoes) {
            val linhaVersao = buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("processo_fase_historico_id", historico.id)
                put("conteudo", novaObservacao)
            }
            client.postgrest["observacao_versoes"].insert(linhaVersao)
        }
        sincronizar(historico.processoId)
    }

    /**
     * Encerra a entrada corrente e abre uma nova para [faseDestinoId] via a
     * RPC `avancar_fase()` (transação atômica no servidor) — nunca
     * automático, sempre por escolha explícita do usuário. [executorId] é
     * quem executa esta passagem específica (pode divergir do responsável
     * do processo). [motivoRetorno] só é preenchido quando é um retrocesso.
     * A RPC sempre usa a data atual do servidor — não aceita data customizada.
     */
    suspend fun mudarFase(
        processo: ProcessoEntity,
        faseDestinoId: String,
        executorId: String?,
        prazoLimite: LocalDate?,
        motivoRetorno: String?,
        notificarPrazo: Boolean
    ) {
        client.postgrest.rpc(
            "avancar_fase",
            buildJsonObject {
                put("p_processo_id", processo.id)
                put("p_fase_destino_id", faseDestinoId)
                put("p_executor_id", executorId)
                put("p_observacao_inicial", "")
                put("p_prazo_limite", prazoLimite?.toString())
                put("p_notificar_prazo", notificarPrazo)
                put("p_motivo_retorno", motivoRetorno)
            }
        )
        // Relê do servidor em vez de adivinhar `faseAtualId` localmente: a RPC
        // também bumpa `atualizado_em` e pode derivar outros campos.
        sincronizarProcesso(processo.id)
        sincronizar(processo.id)
    }
}
