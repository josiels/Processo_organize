package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.FaseEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaseDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val nome: String,
    val ordem: Int,
    val descricao: String?,
    @SerialName("dias_alerta_atencao") val diasAlertaAtencao: Int,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int
)

fun FaseDto.paraEntity(): FaseEntity = FaseEntity(
    id = id,
    organizacaoId = organizacaoId,
    nome = nome,
    ordem = ordem,
    descricao = descricao,
    diasAlertaAtencao = diasAlertaAtencao,
    diasAlertaCritico = diasAlertaCritico
)
