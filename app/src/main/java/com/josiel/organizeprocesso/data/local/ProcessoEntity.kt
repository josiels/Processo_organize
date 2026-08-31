package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.Instant
import java.time.LocalDate

/**
 * ARQUITETURA.md, seção 4 — Processo (dados-mãe).
 * `fase_atual_id` não faz CASCADE: excluir uma Fase em uso não deve arrancar
 * processos junto — a tela de cadastro de Fases é quem deve impedir isso.
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
    indices = [Index("faseAtualId")]
)
data class ProcessoEntity(
    @PrimaryKey val id: String,
    val numero: String,
    val objeto: String,
    val descricao: String,
    val orgaoDemandante: String,
    val tipo: String,
    val valorEstimadoTotal: Double,
    val dataAbertura: LocalDate,
    val faseAtualId: String,
    val statusGeral: StatusGeralProcesso,
    val createdAt: Instant,
    val updatedAt: Instant,
    val synced: Boolean,
    val deviceOrigin: String
)
