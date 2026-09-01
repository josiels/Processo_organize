package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ARQUITETURA.md, seção 4 — Fase (catálogo reutilizável, cadastro manual).
 * `campos_habilitados` não existe aqui de propósito: a liberação de campos
 * por fase é lógica fixa no código (ver ARQUITETURA.md, regra de negócio 1).
 * `organizacaoId` chegou com o pivô multiusuário — cada organização tem seu
 * próprio catálogo de fases.
 */
@Entity(
    tableName = "fases",
    indices = [Index("organizacaoId")]
)
data class FaseEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val ordem: Int,
    val descricao: String?,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int
)
