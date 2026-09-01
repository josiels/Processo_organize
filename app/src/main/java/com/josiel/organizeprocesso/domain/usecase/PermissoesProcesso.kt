package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.Papel

/**
 * Regras de UI para decidir quais ações mostrar — espelham exatamente a RLS
 * do backend, nunca a substituem (spec do Plano 2B, seção 2; a RLS é a
 * barreira real, isto só evita que o usuário tente uma ação que o servidor
 * vai rejeitar). `Papel.SUPER_ADMIN` cai nos ramos negativos em todas as
 * funções: não participa do dia a dia de nenhuma organização (spec do
 * pivô, seção 2) e a RLS do backend também só reconhece `= 'admin'`.
 */
fun podeCriarProcesso(papel: Papel): Boolean = papel == Papel.ADMIN

/** Espelha a política `processos_update`: admin, dono atual, ou qualquer um se o processo está órfão. */
fun podeEditarProcesso(papel: Papel, responsavelId: String?, usuarioId: String): Boolean =
    papel == Papel.ADMIN || responsavelId == null || responsavelId == usuarioId

/** Ação de designação disponível no Detalhe do Processo (spec do Plano 2B, seção 4.3). */
enum class AcaoDesignacao { DESIGNAR, ASSUMIR, DEVOLVER, NENHUMA }

fun acaoDesignacaoDisponivel(papel: Papel, responsavelId: String?, usuarioId: String): AcaoDesignacao = when {
    papel == Papel.ADMIN -> AcaoDesignacao.DESIGNAR
    responsavelId == null -> AcaoDesignacao.ASSUMIR
    responsavelId == usuarioId -> AcaoDesignacao.DEVOLVER
    else -> AcaoDesignacao.NENHUMA
}
