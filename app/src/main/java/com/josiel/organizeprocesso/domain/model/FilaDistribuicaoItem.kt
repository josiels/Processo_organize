package com.josiel.organizeprocesso.domain.model

import java.time.Instant

/** Uma linha da Fila de Distribuição (spec do Plano 2C, seção 4) — já resolvida para exibição. */
data class FilaDistribuicaoItem(
    val perfilId: String,
    val nome: String,
    val ultimoRecebimentoEm: Instant?,
    val criadoEm: Instant,
    val totalDesignacoes: Long
)
