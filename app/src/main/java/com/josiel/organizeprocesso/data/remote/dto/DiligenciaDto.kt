package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiligenciaDto(
    val id: String,
    @SerialName("processo_fase_historico_id") val processoFaseHistoricoId: String,
    @SerialName("autor_id") val autorId: String,
    val conteudo: String,
    @SerialName("criado_em") val criadoEm: String
)

fun DiligenciaDto.paraEntity(): DiligenciaEntity = DiligenciaEntity(
    id = id,
    processoFaseHistoricoId = processoFaseHistoricoId,
    autorId = autorId,
    conteudo = conteudo,
    criadoEm = Instant.parse(criadoEm)
)
