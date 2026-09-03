package com.josiel.organizeprocesso.domain.usecase

/**
 * Decide a fase a usar no formulário de criação/edição de processo quando o
 * usuário troca de Tipo de Processo (spec de tipo-processo-simples, seção
 * 4) — tipo simples sempre usa sua fase única (mesmo que já houvesse uma
 * fase selecionada manualmente antes da troca); tipo com etapas volta a
 * exigir escolha manual, por isso retorna null mesmo que uma fase-padrão
 * esteja presente (ela pertence a outro tipo, não a este).
 */
fun decidirFaseAoTrocarTipo(tipoSimples: Boolean, fasePadraoId: String?): String? =
    if (tipoSimples) fasePadraoId else null
