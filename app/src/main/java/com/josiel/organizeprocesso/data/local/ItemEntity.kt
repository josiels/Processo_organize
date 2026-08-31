package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * ARQUITETURA.md, seção 4 — Item (1:N com Processo).
 * `valorPesquisaUnit` é nullable: só é preenchido a partir da fase de
 * pesquisa de preços — a trava de edição é regra fixa no código (ver
 * ARQUITETURA.md, regra de negócio 1), não uma constraint de banco.
 */
@Entity(
    tableName = "itens",
    foreignKeys = [
        ForeignKey(
            entity = ProcessoEntity::class,
            parentColumns = ["id"],
            childColumns = ["processoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("processoId")]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val processoId: String,
    val descricao: String,
    val quantidade: Double,
    val unidade: String,
    val valorEstimadoUnit: Double,
    val valorPesquisaUnit: Double?,
    val updatedAt: Instant,
    val synced: Boolean,
    val deviceOrigin: String
)
