package com.josiel.organizeprocesso.data.remote.dto

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DiligenciaDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = DiligenciaDto(
            id = "d1",
            processoFaseHistoricoId = "h1",
            autorId = "perfil1",
            conteudo = "Enviado ofício ao fornecedor X",
            criadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("d1", entity.id)
        assertEquals("h1", entity.processoFaseHistoricoId)
        assertEquals("perfil1", entity.autorId)
        assertEquals("Enviado ofício ao fornecedor X", entity.conteudo)
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), entity.criadoEm)
    }
}
