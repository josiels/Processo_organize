package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ARQUITETURA.md, seção 4 — ProcessoFaseHistorico (núcleo do histórico de
 * fases). `dataSaida == null` marca a fase corrente/ativa do processo.
 * `responsavelId` aqui é "quem executou esta passagem específica pela fase"
 * — um conceito DIFERENTE de `ProcessoEntity.responsavelId` (a designação
 * corrente do processo inteiro). Ver spec do Plano 2A, seção 2.3.
 */
@Entity(
    tableName = "processo_fase_historico",
    foreignKeys = [
        ForeignKey(
            entity = ProcessoEntity::class,
            parentColumns = ["id"],
            childColumns = ["processoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["faseId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = PerfilEntity::class,
            parentColumns = ["id"],
            childColumns = ["responsavelId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("processoId"), Index("faseId"), Index("responsavelId")]
)
data class ProcessoFaseHistoricoEntity(
    @PrimaryKey val id: String,
    val processoId: String,
    val faseId: String,
    val responsavelId: String?,
    val dataEntrada: LocalDate,
    val dataSaida: LocalDate?,
    val prazoLimite: LocalDate?,
    val observacoes: String,
    val motivoRetorno: String?,
    val notificarPrazo: Boolean
)
