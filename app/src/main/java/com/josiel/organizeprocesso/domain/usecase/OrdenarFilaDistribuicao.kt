package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem

/**
 * Ordena a Fila de Distribuição: quem está há mais tempo sem receber
 * processo aparece primeiro. Quem nunca recebeu nada (`ultimoRecebimentoEm
 * == null`) vem sempre antes de quem já recebeu, ordenado entre si pela
 * data de criação da conta (spec do Plano 2C, seção 4).
 */
fun ordenarFilaDistribuicao(itens: List<FilaDistribuicaoItem>): List<FilaDistribuicaoItem> =
    itens.sortedWith(
        compareBy(
            { it.ultimoRecebimentoEm != null },
            { it.ultimoRecebimentoEm ?: it.criadoEm }
        )
    )
