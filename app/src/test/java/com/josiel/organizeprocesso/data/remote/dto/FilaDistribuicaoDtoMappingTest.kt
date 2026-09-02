package com.josiel.organizeprocesso.data.remote.dto

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilaDistribuicaoDtoMappingTest {
    @Test
    fun `mapeia dto para item de dominio preservando todos os campos`() {
        val dto = FilaDistribuicaoDto(
            perfilId = "p1",
            organizacaoId = "org1",
            nome = "Ana",
            ultimoRecebimentoEm = "2026-09-01T10:00:00Z",
            criadoEm = "2026-01-01T00:00:00Z",
            totalDesignacoes = 3
        )

        val item = dto.paraItem()

        assertEquals("p1", item.perfilId)
        assertEquals("Ana", item.nome)
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), item.ultimoRecebimentoEm)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), item.criadoEm)
        assertEquals(3L, item.totalDesignacoes)
    }

    @Test
    fun `ultimo recebimento nulo mapeia para null`() {
        val dto = FilaDistribuicaoDto(
            perfilId = "p2",
            organizacaoId = "org1",
            nome = "Bruno",
            ultimoRecebimentoEm = null,
            criadoEm = "2026-01-01T00:00:00Z",
            totalDesignacoes = 0
        )

        assertNull(dto.paraItem().ultimoRecebimentoEm)
    }
}
