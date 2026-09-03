package com.josiel.organizeprocesso.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecidirFaseAoTrocarTipoTest {
    @Test
    fun `tipo simples usa a fase padrao do tipo`() {
        assertEquals(
            "fase-1",
            decidirFaseAoTrocarTipo(tipoSimples = true, fasePadraoId = "fase-1")
        )
    }

    @Test
    fun `tipo com etapas exige escolha manual (retorna null)`() {
        assertNull(decidirFaseAoTrocarTipo(tipoSimples = false, fasePadraoId = null))
    }

    @Test
    fun `tipo com etapas ignora uma fase padrao presente`() {
        assertNull(decidirFaseAoTrocarTipo(tipoSimples = false, fasePadraoId = "fase-1"))
    }
}
