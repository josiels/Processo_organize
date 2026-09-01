package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache local de `public.tipos_processo` (backend) — catálogo cadastrado pelo admin. */
@Entity(tableName = "tipos_processo")
data class TipoProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int
)
