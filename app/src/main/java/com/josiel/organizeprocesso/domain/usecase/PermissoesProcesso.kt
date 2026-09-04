package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.Papel

/**
 * Regras de UI para decidir quais ações mostrar — espelham exatamente a RLS
 * do backend, nunca a substituem (spec do Plano 2B, seção 2; a RLS é a
 * barreira real, isto só evita que o usuário tente uma ação que o servidor
 * vai rejeitar).
 *
 * `Papel.SUPER_ADMIN` NÃO tem tratamento especial aqui: como a RLS do backend
 * só reconhece `= 'admin'`, ele é tratado como qualquer papel não privilegiado
 * em cada função — o que significa que ele cai no ramo negativo de
 * [podeCriarProcesso], mas segue as MESMAS regras de dono/órfão dos demais em
 * [podeEditarProcesso] (true num processo órfão) e em
 * [acaoDesignacaoDisponivel] (ASSUMIR num processo órfão). Isso é inofensivo
 * porque o super_admin não tem `organizacao_id` e a RLS bloqueia a escrita de
 * qualquer jeito: o backstop é o servidor, não este código.
 */
fun podeCriarProcesso(papel: Papel): Boolean = papel == Papel.ADMIN

/** Espelha a política `processos_update`: admin, dono atual, ou qualquer um se o processo está órfão. */
fun podeEditarProcesso(papel: Papel, responsavelId: String?, usuarioId: String): Boolean =
    papel == Papel.ADMIN || responsavelId == null || responsavelId == usuarioId

/**
 * Espelha a checagem própria de `avancar_fase()` — deliberadamente SEM a
 * exceção de processo órfão que [podeEditarProcesso] tem: avançar/retornar
 * fase é restrito ao responsável atual (ou admin); um processo sem
 * responsável precisa ser assumido antes.
 */
fun podeAvancarFase(papel: Papel, responsavelId: String?, usuarioId: String): Boolean =
    papel == Papel.ADMIN || responsavelId == usuarioId

/** Ação de designação disponível no Detalhe do Processo (spec do Plano 2B, seção 4.3). */
enum class AcaoDesignacao { DESIGNAR, ASSUMIR, DEVOLVER, NENHUMA }

fun acaoDesignacaoDisponivel(papel: Papel, responsavelId: String?, usuarioId: String): AcaoDesignacao = when {
    papel == Papel.ADMIN -> AcaoDesignacao.DESIGNAR
    responsavelId == null -> AcaoDesignacao.ASSUMIR
    responsavelId == usuarioId -> AcaoDesignacao.DEVOLVER
    else -> AcaoDesignacao.NENHUMA
}
