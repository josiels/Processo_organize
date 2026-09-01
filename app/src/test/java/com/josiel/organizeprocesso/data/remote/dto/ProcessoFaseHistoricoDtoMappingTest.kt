package com.josiel.organizeprocesso.data.remote.dto

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessoFaseHistoricoDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = ProcessoFaseHistoricoDto(
            id = "h1",
            processoId = "p1",
            faseId = "f1",
            responsavelId = "perfil1",
            dataEntrada = "2026-09-01",
            dataSaida = null,
            prazoLimite = "2026-09-15",
            observacoes = "Em análise",
            motivoRetorno = null,
            notificarPrazo = true,
            criadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("h1", entity.id)
        assertEquals("p1", entity.processoId)
        assertEquals("perfil1", entity.responsavelId)
        assertEquals(LocalDate.of(2026, 9, 1), entity.dataEntrada)
        assertNull(entity.dataSaida)
        assertEquals(LocalDate.of(2026, 9, 15), entity.prazoLimite)
        assertEquals(true, entity.notificarPrazo)
    }
}
