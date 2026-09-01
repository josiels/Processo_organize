package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.remote.dto.ProcessoDto
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessoDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = ProcessoDto(
            id = "p1",
            organizacaoId = "org1",
            numero = "001/2026",
            objeto = "Aquisição de equipamentos",
            descricao = "",
            orgaoDemandante = "",
            tipoProcessoId = "tipo1",
            valorEstimadoTotal = 1000.0,
            dataAbertura = "2026-09-01",
            faseAtualId = "fase1",
            statusGeral = "em_andamento",
            responsavelId = null,
            designadoEm = null,
            designadoPor = null,
            criadoEm = "2026-09-01T10:00:00Z",
            atualizadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("p1", entity.id)
        assertEquals("org1", entity.organizacaoId)
        assertEquals("tipo1", entity.tipoProcessoId)
        assertEquals(StatusGeralProcesso.EM_ANDAMENTO, entity.statusGeral)
        assertEquals(LocalDate.of(2026, 9, 1), entity.dataAbertura)
        assertEquals(null, entity.responsavelId)
    }
}
