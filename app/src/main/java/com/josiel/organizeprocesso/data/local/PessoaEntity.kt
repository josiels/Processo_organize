package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** ARQUITETURA.md, seção 4 — Pessoa. */
@Entity(tableName = "pessoas")
data class PessoaEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val cargoSetor: String?,
    val ativo: Boolean,
    val updatedAt: Instant,
    val synced: Boolean,
    val deviceOrigin: String
)
