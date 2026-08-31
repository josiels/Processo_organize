package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * ARQUITETURA.md, seção 4 — Fase (catálogo reutilizável, cadastro manual).
 * `campos_habilitados` não existe aqui de propósito: a liberação de campos
 * por fase é lógica fixa no código (ver ARQUITETURA.md, regra de negócio 1).
 */
@Entity(tableName = "fases")
data class FaseEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val ordem: Int,
    val descricao: String?,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int,
    val padrao: Boolean,
    val updatedAt: Instant,
    val synced: Boolean,
    val deviceOrigin: String
)
