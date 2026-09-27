package com.noctua.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.noctua.api.config.JwtProperties
import com.noctua.api.dto.DocumentoOrigemResponse
import com.noctua.api.dto.FuncionarioResponse
import com.noctua.api.dto.MembroEquipeResponse
import com.noctua.api.dto.ProjetoDetalheResponse
import com.noctua.api.dto.ProjetoResumoResponse
import com.noctua.api.dto.RecursoNaoEncontradoException
import com.noctua.api.security.JwtService
import com.noctua.api.services.FuncionarioService
import com.noctua.api.services.ProjetoService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(
    controllers = [ProjetoController::class, FuncionarioController::class],
    properties = [
        "spring.autoconfigure.exclude=" +
            "com.noctua.api.config.SecurityConfig," +
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
    ]
)
@Import(ProjetoFuncionarioControllerTest.ContextoDeTeste::class)
class ProjetoFuncionarioControllerTest {

    @TestConfiguration
    class ContextoDeTeste {
        @Bean fun projetoService(): ProjetoService = mockk()
        @Bean fun funcionarioService(): FuncionarioService = mockk()

        @Bean
        fun jwtService(): JwtService = JwtService(
            JwtProperties(
                secret = "noctua-teste-chave-secreta-com-32-bytes-ou-mais",
                expirationMinutes = 60,
                issuer = "noctua-api"
            )
        )
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var projetoService: ProjetoService
    @Autowired private lateinit var funcionarioService: FuncionarioService

    private val resumo = ProjetoResumoResponse(
        id = "p1",
        nome = "Portal Cliente",
        cliente = "Empresa A",
        status = "Em dia",
        progresso = 78,
        orcamento = BigDecimal("800000.00"),
        gasto = BigDecimal("400000.00"),
        deadline = LocalDate.of(2026, 11, 30),
        fase = "Desenvolvimento",
        equipe = listOf("u1", "u2", "u3")
    )

    // -----------------------------------------------------------------
    // GET /api/v1/projetos
    // -----------------------------------------------------------------

    @Test
    fun `GET projetos devolve a equipe como lista de IDs`() {
        every { projetoService.listar() } returns listOf(resumo)

        mockMvc.perform(get("/api/v1/projetos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("p1"))
            .andExpect(jsonPath("$[0].nome").value("Portal Cliente"))
            .andExpect(jsonPath("$[0].cliente").value("Empresa A"))
            .andExpect(jsonPath("$[0].fase").value("Desenvolvimento"))
            .andExpect(jsonPath("$[0].status").value("Em dia"))
            .andExpect(jsonPath("$[0].orcamento").value(800000.00))
            .andExpect(jsonPath("$[0].deadline").value("2026-11-30"))
            .andExpect(jsonPath("$[0].equipe.length()").value(3))
            .andExpect(jsonPath("$[0].equipe[0]").value("u1"))
            .andExpect(jsonPath("$[0].equipe[2]").value("u3"))
    }

    @Test
    fun `GET projetos nao expoe a contagem de equipe, conforme decisao de contrato`() {
        every { projetoService.listar() } returns listOf(resumo)

        val corpo = mockMvc.perform(get("/api/v1/projetos"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // `quantidade_equipe` foi removido: o frontend deriva de equipe.length.
        assertThat(corpo).doesNotContain("quantidade_equipe")
    }

    @Test
    fun `GET projetos devolve array vazio quando nao ha projetos`() {
        every { projetoService.listar() } returns emptyList()

        mockMvc.perform(get("/api/v1/projetos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // -----------------------------------------------------------------
    // GET /api/v1/projetos/{id}
    // -----------------------------------------------------------------

    @Test
    fun `GET projetos por id devolve equipe detalhada e tecnologias`() {
        every { projetoService.buscarPorId("p1") } returns ProjetoDetalheResponse(
            id = "p1",
            nome = "Portal Cliente",
            cliente = "Empresa A",
            status = "Em dia",
            progresso = 78,
            orcamento = BigDecimal("800000.00"),
            gasto = BigDecimal("400000.00"),
            deadline = LocalDate.of(2026, 11, 30),
            fase = "Desenvolvimento",
            documentoOrigem = DocumentoOrigemResponse(
                bucket = "noctua-projetos-documentos",
                objectKey = "2026/portal-cliente/documentacao.pdf",
                lastModified = Instant.parse("2026-01-12T09:15:00Z")
            ),
            equipe = listOf(
                MembroEquipeResponse("u1", "Ana Silva", "Analista", "TI", "Estagiário"),
                MembroEquipeResponse("u2", "Bruno Costa", "Desenvolvedor", "Dados", "Júnior")
            ),
            tecnologias = listOf("Node.js", "React", "Postgres")
        )

        mockMvc.perform(get("/api/v1/projetos/p1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("p1"))
            .andExpect(jsonPath("$.nome").value("Portal Cliente"))
            .andExpect(jsonPath("$.equipe.length()").value(2))
            .andExpect(jsonPath("$.equipe[0].nome").value("Ana Silva"))
            .andExpect(jsonPath("$.equipe[0].cargo").value("Analista"))
            .andExpect(jsonPath("$.equipe[0].departamento").value("TI"))
            .andExpect(jsonPath("$.equipe[0].senioridade").value("Estagiário"))
            .andExpect(jsonPath("$.tecnologias.length()").value(3))
            .andExpect(jsonPath("$.tecnologias[0]").value("Node.js"))
            .andExpect(jsonPath("$.documento_origem.bucket").value("noctua-projetos-documentos"))
            .andExpect(jsonPath("$.documento_origem.object_key").value("2026/portal-cliente/documentacao.pdf"))
            .andExpect(jsonPath("$.documento_origem.last_modified").value("2026-01-12T09:15:00Z"))
    }

    @Test
    fun `GET projetos por id inexistente devolve 404 no formato de erro da API`() {
        every { projetoService.buscarPorId("p99") } throws
            RecursoNaoEncontradoException("Projeto p99 nao encontrado")

        mockMvc.perform(get("/api/v1/projetos/p99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.erro").value("Recurso nao encontrado"))
            .andExpect(jsonPath("$.mensagem").value("Projeto p99 nao encontrado"))
            .andExpect(jsonPath("$.caminho").value("/api/v1/projetos/p99"))
    }

    // -----------------------------------------------------------------
    // GET /api/v1/funcionarios
    // -----------------------------------------------------------------

    @Test
    fun `GET funcionarios devolve o quadro com area, senioridade e disponibilidade`() {
        every { funcionarioService.listar() } returns listOf(
            FuncionarioResponse(
                id = "u1",
                nome = "Ana Silva",
                cargo = "Analista",
                departamento = "TI",
                senioridade = "Estagiário",
                tecnologia = "Node.js",
                disponibilidade = 90,
                dataIngresso = LocalDate.of(2021, 1, 1),
                localizacao = "SP"
            )
        )

        mockMvc.perform(get("/api/v1/funcionarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("u1"))
            .andExpect(jsonPath("$[0].nome").value("Ana Silva"))
            .andExpect(jsonPath("$[0].departamento").value("TI"))
            .andExpect(jsonPath("$[0].senioridade").value("Estagiário"))
            .andExpect(jsonPath("$[0].disponibilidade").value(90))
            .andExpect(jsonPath("$[0].data_ingresso").value("2021-01-01"))
            .andExpect(jsonPath("$[0].localizacao").value("SP"))
    }

    @Test
    fun `GET funcionarios omite o senioridade quando o funcionario nao tem nivel`() {
        every { funcionarioService.listar() } returns listOf(
            FuncionarioResponse(
                id = "u9",
                nome = "Igor Santos",
                cargo = "Designer",
                departamento = "Design",
                senioridade = null,
                tecnologia = "Figma",
                disponibilidade = 60,
                dataIngresso = LocalDate.of(2022, 4, 1),
                localizacao = "RJ"
            )
        )

        mockMvc.perform(get("/api/v1/funcionarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("u9"))
            .andExpect(jsonPath("$[0].senioridade").doesNotExist())
    }
}
