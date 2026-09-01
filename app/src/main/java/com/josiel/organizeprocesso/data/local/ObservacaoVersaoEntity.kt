package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * ARQUITETURA.md, seção 4 — ObservacaoVersao. Registro append-only: uma nova
 * edição de observação cria uma linha nova, nunca sobrescreve uma existente
 * (REQUISITOS.md, seção 7 — conflito em texto livre nunca sobrescreve).
 * Por isso não tem `updatedAt` — `criadoEm` já é a marca de tempo definitiva.
 */
@Entity(
    tableName = "observacao_versoes",
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
data class ObservacaoVersaoEntity(
    @PrimaryKey val id: String,
    val processoFaseHistoricoId: String,
    val conteudo: String,
    val criadoEm: Instant
)
