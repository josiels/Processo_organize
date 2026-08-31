package com.josiel.organizeprocesso.domain.usecase

import java.text.Normalizer

/**
 * ARQUITETURA.md, regra de negócio 1 — bloqueio de campos por fase.
 *
 * Fases são 100% cadastradas manualmente pelo usuário (REQUISITOS.md, seção
 * 3) — não existe um id fixo de "fase de pesquisa de preços" no schema. A
 * lógica fixa no código (não configurável) compara o NOME da fase de forma
 * normalizada (sem acento, sem caixa) contra o termo esperado, já que este é
 * um app de uso pessoal onde o usuário sempre nomeia essa fase da mesma
 * forma na prática.
 */
object RegrasBloqueioCampos {
    private const val TERMO_FASE_PESQUISA_PRECOS = "pesquisa de preco"

    fun ehFaseDePesquisaDePrecos(nomeFase: String): Boolean =
        normalizar(nomeFase).contains(TERMO_FASE_PESQUISA_PRECOS)

    /**
     * `Item.valorPesquisaUnit` só é editável quando o processo já alcançou
     * (está ou já passou pela) a fase de pesquisa de preços.
     */
    fun valorPesquisaLiberado(nomesFasesRelevantes: Collection<String>): Boolean =
        nomesFasesRelevantes.any(::ehFaseDePesquisaDePrecos)

    private fun normalizar(texto: String): String {
        val semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return semAcento.trim().lowercase()
    }
}
