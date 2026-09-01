package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.Papel
import java.time.Instant

/**
 * Cache local de `public.perfis` (backend) — só leitura no app; criação é
 * exclusivamente via Edge Function `criar-conta`/`criar-organizacao`, nunca
 * um formulário CRUD direto (ver spec do Plano 2A, seção 2.3).
 */
@Entity(tableName = "perfis")
data class PerfilEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String?,
    val papel: Papel,
    val nome: String,
    val cargoSetor: String?,
    val ativo: Boolean,
    val notificarAvancoFase: Boolean,
    val notificarPrazo: Boolean,
    val notificarTempoParado: Boolean,
    val ultimoRecebimentoEm: Instant?,
    val criadoEm: Instant
)
