package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha as colunas de `public.perfis` que o app lê via Postgrest. */
@Serializable
data class PerfilDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    val papel: String,
    val nome: String,
    @SerialName("cargo_setor") val cargoSetor: String?,
    val ativo: Boolean,
    @SerialName("notificar_avanco_fase") val notificarAvancoFase: Boolean,
    @SerialName("notificar_prazo") val notificarPrazo: Boolean,
    @SerialName("notificar_tempo_parado") val notificarTempoParado: Boolean,
    @SerialName("ultimo_recebimento_em") val ultimoRecebimentoEm: String?,
    @SerialName("criado_em") val criadoEm: String
)
