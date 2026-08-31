package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.TipoAnexo
import java.time.Instant

/**
 * ARQUITETURA.md, seção 4 — AnexoLink, vinculado à fase específica do
 * processo (não ao processo como um todo — REQUISITOS.md, seção 5).
 */
@Entity(
    tableName = "anexos_links",
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
data class AnexoLinkEntity(
    @PrimaryKey val id: String,
    val processoFaseHistoricoId: String,
    val tipo: TipoAnexo,
    val urlOuPath: String,
    val descricao: String,
    val updatedAt: Instant,
    val synced: Boolean,
    val deviceOrigin: String
)
