package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessoDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val numero: String,
    val objeto: String,
    val descricao: String,
    @SerialName("orgao_demandante") val orgaoDemandante: String,
    @SerialName("tipo_processo_id") val tipoProcessoId: String,
    @SerialName("valor_estimado_total") val valorEstimadoTotal: Double,
    @SerialName("data_abertura") val dataAbertura: String,
    @SerialName("fase_atual_id") val faseAtualId: String,
    @SerialName("status_geral") val statusGeral: String,
    @SerialName("responsavel_id") val responsavelId: String?,
    @SerialName("designado_em") val designadoEm: String?,
    @SerialName("designado_por") val designadoPor: String?,
    @SerialName("criado_em") val criadoEm: String,
    @SerialName("atualizado_em") val atualizadoEm: String
)
