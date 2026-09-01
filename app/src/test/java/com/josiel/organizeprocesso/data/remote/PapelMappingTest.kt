package com.josiel.organizeprocesso.data.remote

import com.josiel.organizeprocesso.domain.model.Papel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PapelMappingTest {
    @Test
    fun `mapeia super_admin`() {
        assertEquals(Papel.SUPER_ADMIN, papelDoTexto("super_admin"))
    }

    @Test
    fun `mapeia admin`() {
        assertEquals(Papel.ADMIN, papelDoTexto("admin"))
    }

    @Test
    fun `mapeia usuario`() {
        assertEquals(Papel.USUARIO, papelDoTexto("usuario"))
    }

    @Test
    fun `valor desconhecido lanca excecao`() {
        assertThrows(IllegalArgumentException::class.java) {
            papelDoTexto("papel-que-nao-existe")
        }
    }
}
