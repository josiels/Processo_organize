package com.josiel.organizeprocesso.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampoValorMonetarioTest {

    // O separador entre "R$" e o número varia por JDK (espaço comum vs. NBSP
    // vs. espaço estreito das versões mais novas do Unicode CLDR) — os
    // testes abaixo checam dígitos/pontuação, não o caractere de espaço
    // exato, pra não ficarem reféns dessa diferença entre ambientes.

    @Test
    fun `formatarComoReais formata valor com milhar e centavos`() {
        val resultado = formatarComoReais(1234.56)
        assertTrue(resultado.startsWith("R$"))
        assertTrue(resultado.endsWith("1.234,56"))
    }

    @Test
    fun `formatarComoReais formata zero`() {
        val resultado = formatarComoReais(0.0)
        assertTrue(resultado.startsWith("R$"))
        assertTrue(resultado.endsWith("0,00"))
    }

    @Test
    fun `valorMonetarioParaDouble extrai o valor de um texto formatado`() {
        assertEquals(1234.56, valorMonetarioParaDouble("R$ 1.234,56"), 0.001)
    }

    @Test
    fun `valorMonetarioParaDouble retorna zero para texto vazio`() {
        assertEquals(0.0, valorMonetarioParaDouble(""), 0.001)
    }

    @Test
    fun `round trip formatar e extrair preserva o valor`() {
        val original = 987654.32
        val formatado = formatarComoReais(original)
        assertEquals(original, valorMonetarioParaDouble(formatado), 0.001)
    }
}
