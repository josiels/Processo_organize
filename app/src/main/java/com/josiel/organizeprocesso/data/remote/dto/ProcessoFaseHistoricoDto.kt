package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessoFaseHistoricoDto(
    val id: String,
    @SerialName("processo_id") val processoId: String,
    @SerialName("fase_id") val faseId: String,
    @SerialName("responsavel_id") val responsavelId: String?,
    @SerialName("data_entrada") val dataEntrada: String,
    @SerialName("data_saida") val dataSaida: String?,
    @SerialName("prazo_limite") val prazoLimite: String?,
    val observacoes: String,
    @SerialName("motivo_retorno") val motivoRetorno: String?,
    @SerialName("notificar_prazo") val notificarPrazo: Boolean,
    @SerialName("criado_em") val criadoEm: String
)

fun ProcessoFaseHistoricoDto.paraEntity(): ProcessoFaseHistoricoEntity = ProcessoFaseHistoricoEntity(
    id = id,
    processoId = processoId,
    faseId = faseId,
    responsavelId = responsavelId,
    dataEntrada = LocalDate.parse(dataEntrada),
    dataSaida = dataSaida?.let(LocalDate::parse),
    prazoLimite = prazoLimite?.let(LocalDate::parse),
    observacoes = observacoes,
    motivoRetorno = motivoRetorno,
    notificarPrazo = notificarPrazo
)
