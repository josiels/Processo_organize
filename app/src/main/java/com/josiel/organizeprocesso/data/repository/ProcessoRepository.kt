package com.josiel.organizeprocesso.data.repository

import androidx.room.withTransaction
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Abstrai a origem dos dados de Processo — hoje só Room; Supabase entra na
 * etapa de sincronização. Também orquestra a criação do primeiro registro de
 * [ProcessoFaseHistoricoEntity] (ARQUITETURA.md, seção 4) quando um processo
 * é criado, já que toda fase corrente precisa de uma entrada de histórico.
 */
class ProcessoRepository(
    private val database: AppDatabase,
    private val deviceId: String
) {
    private val processoDao = database.processoDao()
    private val itemDao = database.itemDao()
    private val historicoDao = database.processoFaseHistoricoDao()

    fun observarTodos(): Flow<List<ProcessoEntity>> = processoDao.observarTodos()
    fun observarPorId(id: String): Flow<ProcessoEntity?> = processoDao.observarPorId(id)
    fun observarItens(processoId: String): Flow<List<ItemEntity>> = itemDao.observarPorProcesso(processoId)

    suspend fun criar(
        numero: String,
        objeto: String,
        descricao: String,
        orgaoDemandante: String,
        tipo: String,
        dataAbertura: LocalDate,
        faseInicialId: String,
        statusGeral: StatusGeralProcesso,
        itens: List<ItemEntity>
    ): String {
        val processoId = UUID.randomUUID().toString()
        val agora = Instant.now()
        val valorTotal = itens.sumOf { it.quantidade * it.valorEstimadoUnit }

        database.withTransaction {
            processoDao.upsert(
                ProcessoEntity(
                    id = processoId,
                    numero = numero,
                    objeto = objeto,
                    descricao = descricao,
                    orgaoDemandante = orgaoDemandante,
                    tipo = tipo,
                    valorEstimadoTotal = valorTotal,
                    dataAbertura = dataAbertura,
                    faseAtualId = faseInicialId,
                    statusGeral = statusGeral,
                    createdAt = agora,
                    updatedAt = agora,
                    synced = false,
                    deviceOrigin = deviceId
                )
            )
            historicoDao.upsert(
                ProcessoFaseHistoricoEntity(
                    id = UUID.randomUUID().toString(),
                    processoId = processoId,
                    faseId = faseInicialId,
                    responsavelId = null,
                    dataEntrada = dataAbertura,
                    dataSaida = null,
                    prazoLimite = null,
                    observacoes = "",
                    motivoRetorno = null,
                    notificarPrazo = false,
                    updatedAt = agora,
                    synced = false,
                    deviceOrigin = deviceId
                )
            )
            itens.forEach { item ->
                itemDao.upsert(item.copy(processoId = processoId, updatedAt = agora, synced = false, deviceOrigin = deviceId))
            }
        }
        return processoId
    }

    suspend fun atualizar(
        processo: ProcessoEntity,
        itensAtuais: List<ItemEntity>,
        itensRemovidos: List<ItemEntity>
    ) {
        val agora = Instant.now()
        val valorTotal = itensAtuais.sumOf { it.quantidade * it.valorEstimadoUnit }

        database.withTransaction {
            processoDao.upsert(processo.copy(valorEstimadoTotal = valorTotal, updatedAt = agora, synced = false, deviceOrigin = deviceId))
            itensAtuais.forEach { item ->
                itemDao.upsert(item.copy(updatedAt = agora, synced = false, deviceOrigin = deviceId))
            }
            itensRemovidos.forEach { item -> itemDao.delete(item) }
        }
    }

    suspend fun excluir(processo: ProcessoEntity) = processoDao.delete(processo)
}
