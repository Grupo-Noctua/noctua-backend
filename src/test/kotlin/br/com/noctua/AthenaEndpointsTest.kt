package br.com.noctua

import br.com.noctua.controller.ApiExceptionHandler
import br.com.noctua.controller.DashboardController
import br.com.noctua.dto.*
import br.com.noctua.service.DashboardService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(DashboardController::class)
@Import(ApiExceptionHandler::class)
class AthenaEndpointsTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: DashboardService

    @Test
    fun `contratos HTTP dos quatro indicadores de funcionarios`() {
        `when`(service.totalFuncionarios()).thenReturn(TotalDTO(3000000000))
        `when`(service.funcionariosPorTecnologia()).thenReturn(listOf(TecnologiaDTO("Kotlin", 7)))
        `when`(service.funcionariosPorSenioridade()).thenReturn(listOf(SenioridadeDTO("Senior", 5)))
        `when`(service.funcionariosPorArea()).thenReturn(listOf(AreaDTO("Dados", 4)))
        mapOf(
            "total" to """{"total":3000000000}""",
            "tecnologia" to """[{"tecnologia":"Kotlin","quantidade":7}]""",
            "senioridade" to """[{"senioridade":"Senior","quantidade":5}]""",
            "area" to """[{"area":"Dados","quantidade":4}]""",
        ).forEach { (route, body) ->
            mvc.get("/api/dashboard/funcionarios/$route").andExpect {
                status { isOk() }; content { json(body) }
            }
        }
    }

    @Test
    fun `distribuicoes vazias e total zero sao respostas de sucesso`() {
        `when`(service.totalFuncionarios()).thenReturn(TotalDTO(0))
        `when`(service.funcionariosPorTecnologia()).thenReturn(emptyList())
        `when`(service.funcionariosPorSenioridade()).thenReturn(emptyList())
        `when`(service.funcionariosPorArea()).thenReturn(emptyList())
        mvc.get("/api/dashboard/funcionarios/total").andExpect { content { json("""{"total":0}""") } }
        listOf("tecnologia", "senioridade", "area").forEach {
            mvc.get("/api/dashboard/funcionarios/$it").andExpect { status { isOk() }; content { json("[]") } }
        }
    }
}
