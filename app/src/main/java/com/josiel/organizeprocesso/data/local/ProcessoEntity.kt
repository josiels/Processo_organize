package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.Instant
import java.time.LocalDate

/**
 * ARQUITETURA.md, seção 4 — Processo (dados-mãe). `fase_atual_id` não faz
 * CASCADE: excluir uma Fase em uso não deve arrancar processos junto.
 * Campos de designação (`responsavelId`/`designadoEm`/`designadoPor`) são
 * só leitura no app — a única forma sancionada de mudá-los é a RPC
 * `designar_processo()` no backend (Plano 1, Task 9); nenhum código deste
 * plano escreve nesses três campos diretamente.
 */
@Entity(
    tableName = "processos",
    foreignKeys = [
        ForeignKey(
            entity = FaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["faseAtualId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("faseAtualId"), Index("organizacaoId"), Index("responsavelId")]
)
data class ProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val numero: String,
    val objeto: String,
    val descricao: String,
    val orgaoDemandante: String,
    val tipoProcessoId: String,
    val valorEstimadoTotal: Double,
    val dataAbertura: LocalDate,
    val faseAtualId: String,
    val statusGeral: StatusGeralProcesso,
    val responsavelId: String?,
    val designadoEm: Instant?,
    val designadoPor: String?,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)
