package com.josiel.organizeprocesso.domain.model

/** Status geral do processo (REQUISITOS.md, seção 4 — Dados-mãe do processo). */
enum class StatusGeralProcesso {
    EM_ANDAMENTO,
    SUSPENSO,
    CONCLUIDO,
    CANCELADO
}

fun StatusGeralProcesso.rotulo(): String = when (this) {
    StatusGeralProcesso.EM_ANDAMENTO -> "Em andamento"
    StatusGeralProcesso.SUSPENSO -> "Suspenso"
    StatusGeralProcesso.CONCLUIDO -> "Concluído"
    StatusGeralProcesso.CANCELADO -> "Cancelado"
}
