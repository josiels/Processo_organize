package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.ItemEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemDto(
    val id: String,
    @SerialName("processo_id") val processoId: String,
    val descricao: String,
    val quantidade: Double,
    val unidade: String,
    @SerialName("valor_estimado_unit") val valorEstimadoUnit: Double,
    @SerialName("valor_pesquisa_unit") val valorPesquisaUnit: Double?
)

fun ItemDto.paraEntity(processoIdFallback: String): ItemEntity = ItemEntity(
    id = id,
    processoId = processoId.ifBlank { processoIdFallback },
    descricao = descricao,
    quantidade = quantidade,
    unidade = unidade,
    valorEstimadoUnit = valorEstimadoUnit,
    valorPesquisaUnit = valorPesquisaUnit
)
