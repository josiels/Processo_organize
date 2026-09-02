package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha a view `public.fila_distribuicao` — não é cacheada no Room, só consultada ao vivo. */
@Serializable
data class FilaDistribuicaoDto(
    @SerialName("perfil_id") val perfilId: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    val nome: String,
    @SerialName("ultimo_recebimento_em") val ultimoRecebimentoEm: String?,
    @SerialName("criado_em") val criadoEm: String,
    @SerialName("total_designacoes") val totalDesignacoes: Long
)

fun FilaDistribuicaoDto.paraItem(): FilaDistribuicaoItem = FilaDistribuicaoItem(
    perfilId = perfilId,
    nome = nome,
    ultimoRecebimentoEm = ultimoRecebimentoEm?.let(Instant::parse),
    criadoEm = Instant.parse(criadoEm),
    totalDesignacoes = totalDesignacoes
)
