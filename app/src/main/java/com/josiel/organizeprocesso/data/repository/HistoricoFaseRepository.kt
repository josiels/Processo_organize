package com.josiel.organizeprocesso.data.repository

import androidx.room.withTransaction
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ObservacaoVersaoEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Orquestra o histórico de fases de um processo: edição da entrada corrente
 * (com versionamento de observação — ARQUITETURA.md, seção 3) e a transição
 * para uma nova fase (ROADMAP.md, passo 9), que nunca é automática.
 */
class HistoricoFaseRepository(
    private val database: AppDatabase,
    private val deviceId: String
) {
    private val processoDao = database.processoDao()
    private val historicoDao = database.processoFaseHistoricoDao()
    private val observacaoVersaoDao = database.observacaoVersaoDao()

    fun observarAtivoPorProcesso(processoId: String): Flow<ProcessoFaseHistoricoEntity?> =
        historicoDao.observarAtivoPorProcesso(processoId)

    fun observarVersoesObservacao(historicoId: String): Flow<List<ObservacaoVersaoEntity>> =
        observacaoVersaoDao.observarPorHistorico(historicoId)

    /** Atualiza responsável/prazo/notificação/observação da entrada corrente, sem trocar de fase. */
    suspend fun salvarEntradaAtual(
        historico: ProcessoFaseHistoricoEntity,
        responsavelId: String?,
        prazoLimite: LocalDate?,
        notificarPrazo: Boolean,
        novaObservacao: String
    ) {
        val agora = Instant.now()
        database.withTransaction {
            historicoDao.upsert(
                historico.copy(
                    responsavelId = responsavelId,
                    prazoLimite = prazoLimite,
                    notificarPrazo = notificarPrazo,
                    observacoes = novaObservacao,
                    updatedAt = agora,
                    synced = false,
                    deviceOrigin = deviceId
                )
            )
            if (novaObservacao != historico.observacoes) {
                observacaoVersaoDao.upsert(
                    ObservacaoVersaoEntity(
                        id = UUID.randomUUID().toString(),
                        processoFaseHistoricoId = historico.id,
                        conteudo = novaObservacao,
                        criadoEm = agora,
                        deviceOrigin = deviceId,
                        synced = false
                    )
                )
            }
        }
    }

    /**
     * Encerra a entrada corrente e abre uma nova para [faseDestinoId] — nunca
     * automático, sempre por escolha explícita do usuário (REQUISITOS.md,
     * seção 9). [motivoRetorno] só é preenchido quando é um retrocesso.
     */
    suspend fun mudarFase(
        processo: ProcessoEntity,
        historicoAtual: ProcessoFaseHistoricoEntity,
        faseDestinoId: String,
        dataEntrada: LocalDate,
        responsavelId: String?,
        prazoLimite: LocalDate?,
        motivoRetorno: String?,
        notificarPrazo: Boolean
    ) {
        val agora = Instant.now()
        database.withTransaction {
            historicoDao.upsert(
                historicoAtual.copy(
                    dataSaida = dataEntrada,
                    updatedAt = agora,
                    synced = false,
                    deviceOrigin = deviceId
                )
            )
            historicoDao.upsert(
                ProcessoFaseHistoricoEntity(
                    id = UUID.randomUUID().toString(),
                    processoId = processo.id,
                    faseId = faseDestinoId,
                    responsavelId = responsavelId,
                    dataEntrada = dataEntrada,
                    dataSaida = null,
                    prazoLimite = prazoLimite,
                    observacoes = "",
                    motivoRetorno = motivoRetorno?.takeIf { it.isNotBlank() },
                    notificarPrazo = notificarPrazo,
                    updatedAt = agora,
                    synced = false,
                    deviceOrigin = deviceId
                )
            )
            processoDao.upsert(
                processo.copy(faseAtualId = faseDestinoId, updatedAt = agora, synced = false, deviceOrigin = deviceId)
            )
        }
    }
}
