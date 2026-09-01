package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.Papel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissoesProcessoTest {
    @Test
    fun `so admin pode criar processo`() {
        assertTrue(podeCriarProcesso(Papel.ADMIN))
        assertFalse(podeCriarProcesso(Papel.USUARIO))
        assertFalse(podeCriarProcesso(Papel.SUPER_ADMIN))
    }

    @Test
    fun `admin pode editar qualquer processo`() {
        assertTrue(podeEditarProcesso(Papel.ADMIN, responsavelId = "outro", usuarioId = "eu"))
    }

    @Test
    fun `usuario pode editar processo orfao`() {
        assertTrue(podeEditarProcesso(Papel.USUARIO, responsavelId = null, usuarioId = "eu"))
    }

    @Test
    fun `usuario pode editar o proprio processo`() {
        assertTrue(podeEditarProcesso(Papel.USUARIO, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario nao pode editar processo de outra pessoa`() {
        assertFalse(podeEditarProcesso(Papel.USUARIO, responsavelId = "outro", usuarioId = "eu"))
    }

    @Test
    fun `admin sempre pode designar`() {
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = null, usuarioId = "eu"))
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = "outro", usuarioId = "eu"))
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario em processo orfao pode assumir`() {
        assertEquals(AcaoDesignacao.ASSUMIR, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = null, usuarioId = "eu"))
    }

    @Test
    fun `usuario dono pode devolver`() {
        assertEquals(AcaoDesignacao.DEVOLVER, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario em processo de outra pessoa nao tem acao`() {
        assertEquals(AcaoDesignacao.NENHUMA, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = "outro", usuarioId = "eu"))
    }
}
