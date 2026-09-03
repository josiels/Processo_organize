package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache local de `public.tipos_processo` (backend) — catálogo cadastrado
 * pelo admin. `simples`/`fasePadraoId`: ver spec
 * docs/superpowers/specs/2026-09-02-android-tipo-processo-simples-design.md.
 */
@Entity(tableName = "tipos_processo")
data class TipoProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int,
    val simples: Boolean,
    val fasePadraoId: String?
)
