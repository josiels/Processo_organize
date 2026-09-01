package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Nova no pivô multiusuário (spec do pivô, seção 3) — mini-histórico de
 * eventos dentro de uma passagem de fase. Append-only: sem UPDATE/DELETE na
 * UI (spec do Plano 2B, seção 3.2, Decisão).
 */
@Entity(
    tableName = "diligencias",
    foreignKeys = [
        ForeignKey(
            entity = ProcessoFaseHistoricoEntity::class,
            parentColumns = ["id"],
            childColumns = ["processoFaseHistoricoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("processoFaseHistoricoId")]
)
data class DiligenciaEntity(
    @PrimaryKey val id: String,
    val processoFaseHistoricoId: String,
    val autorId: String,
    val conteudo: String,
    val criadoEm: Instant
)
