package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TipoProcessoDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val nome: String,
    @SerialName("dias_alerta_atencao") val diasAlertaAtencao: Int,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int,
    val simples: Boolean = false,
    @SerialName("fase_padrao_id") val fasePadraoId: String? = null
)
