package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha a view `public.fila_distribuicao_por_tipo` — detalhe por tipo de processo de uma pessoa. */
@Serializable
data class FilaDistribuicaoPorTipoDto(
    @SerialName("perfil_id") val perfilId: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    @SerialName("tipo_processo_id") val tipoProcessoId: String,
    @SerialName("tipo_processo_nome") val tipoProcessoNome: String,
    val total: Long
)
