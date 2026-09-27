package com.noctua.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.noctua.api.config.JwtProperties
import com.noctua.api.dto.AreaFuncionarioResponse
import com.noctua.api.dto.CredenciaisInvalidasException
import com.noctua.api.dto.EmailJaCadastradoException
import com.noctua.api.dto.KpiResponse
import com.noctua.api.dto.LoginResponse
import com.noctua.api.dto.RegisterRequest
import com.noctua.api.dto.SenioridadeFuncionarioResponse
import com.noctua.api.dto.TecnologiaFuncionarioResponse
import com.noctua.api.dto.TecnologiaProjetoResponse
import com.noctua.api.dto.UsuarioResponse
import com.noctua.api.security.JwtService
import com.noctua.api.services.AuthService
import com.noctua.api.services.MetricasService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Fatia de web dos controllers de Metricas e Autenticacao.
 *
 * Os mocks entram pelo contexto (`@TestConfiguration`) e nao por `@MockK`,
 * porque os controllers usam injecao por construtor: o contexto Spring e
 * montado antes de qualquer anotacao de mock de teste ser processada.
 *
 * A seguranca real (JWT, BCrypt) e exercitada em
 * `JwtServiceTest` e `AuthServiceTest`; aqui o objetivo e o contrato HTTP e a
 * serializacao JSON.
 */
@WebMvcTest(
    controllers = [MetricasController::class, AuthController::class],
    properties = [
        "spring.autoconfigure.exclude=" +
            "com.noctua.api.config.SecurityConfig," +
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
    ]
)
@Import(MetricasAuthControllerTest.ContextoDeTeste::class)
class MetricasAuthControllerTest {

    @TestConfiguration
    class ContextoDeTeste {
        @Bean fun metricasService(): MetricasService = mockk()
        @Bean fun authService(): AuthService = mockk()

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
    @Autowired private lateinit var metricasService: MetricasService
    @Autowired private lateinit var authService: AuthService

    // -----------------------------------------------------------------
    // Metricas
    // -----------------------------------------------------------------

    @Test
    fun `GET kpis devolve os tres cards com os nomes snake_case do contrato`() {
        every { metricasService.kpis() } returns KpiResponse(
            totalFuncionarios = 80,
            totalProjetosAnalisados = 6,
            ferramentaMaisUtilizada = "Node.js"
        )

        mockMvc.perform(get("/api/v1/metrics/kpis"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_funcionarios").value(80))
            .andExpect(jsonPath("$.total_projetos_analisados").value(6))
            .andExpect(jsonPath("$.ferramenta_mais_utilizada").value("Node.js"))
    }

    @Test
    fun `GET funcionarios-tecnologia devolve array com tecnologia e quantidade`() {
        every { metricasService.funcionariosPorTecnologia() } returns listOf(
            TecnologiaFuncionarioResponse("Node.js", 11),
            TecnologiaFuncionarioResponse("Spark", 8)
        )

        mockMvc.perform(get("/api/v1/metrics/funcionarios-tecnologia"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].tecnologia").value("Node.js"))
            .andExpect(jsonPath("$[0].quantidade").value(11))
            .andExpect(jsonPath("$[1].tecnologia").value("Spark"))
            .andExpect(jsonPath("$[1].quantidade").value(8))
    }

    @Test
    fun `GET funcionarios-area devolve array com area e quantidade`() {
        every { metricasService.funcionariosPorArea() } returns listOf(
            AreaFuncionarioResponse("TI", 16),
            AreaFuncionarioResponse("Dados", 16)
        )

        mockMvc.perform(get("/api/v1/metrics/funcionarios-area"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].area").value("TI"))
            .andExpect(jsonPath("$[0].quantidade").value(16))
    }

    @Test
    fun `GET funcionarios-senioridade devolve sempre os 6 niveis`() {
        every { metricasService.funcionariosPorSenioridade() } returns listOf(
            SenioridadeFuncionarioResponse("Estagiário", 14),
            SenioridadeFuncionarioResponse("Júnior", 14),
            SenioridadeFuncionarioResponse("Pleno", 13),
            SenioridadeFuncionarioResponse("Sênior", 13),
            SenioridadeFuncionarioResponse("Especialista", 13),
            SenioridadeFuncionarioResponse("Tech Lead", 13)
        )

        mockMvc.perform(get("/api/v1/metrics/funcionarios-senioridade"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].senioridade").value("Estagiário"))
            .andExpect(jsonPath("$[5].senioridade").value("Tech Lead"))
            .andExpect(jsonPath("$[5].quantidade").value(13))
    }

    @Test
    fun `GET projetos-tecnologia devolve quantidade e percentual ja calculados`() {
        every { metricasService.projetosPorTecnologia() } returns listOf(
            TecnologiaProjetoResponse("Node.js", 11, 14),
            TecnologiaProjetoResponse("Python", 7, 9)
        )

        mockMvc.perform(get("/api/v1/metrics/projetos-tecnologia"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].tecnologia").value("Node.js"))
            .andExpect(jsonPath("$[0].quantidade").value(11))
            .andExpect(jsonPath("$[0].percentual").value(14))
            .andExpect(jsonPath("$[1].percentidade").doesNotExist())
            .andExpect(jsonPath("$[1].percentual").value(9))
    }

    @Test
    fun `GET metrics com agregado vazio devolve array vazio e nao 500`() {
        every { metricasService.funcionariosPorTecnologia() } returns emptyList()

        mockMvc.perform(get("/api/v1/metrics/funcionarios-tecnologia"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // -----------------------------------------------------------------
    // Autenticacao
    // -----------------------------------------------------------------

    @Test
    fun `POST register devolve 201 e o perfil do usuario criado`() {
        every { authService.registrar(any()) } returns UsuarioResponse(
            id = 1L,
            nome = "Rafael Drummond",
            email = "rafael.drummond@noctua.com",
            papel = "DIRETOR_PROJETOS",
            criadoEm = Instant.parse("2026-01-05T10:00:00Z")
        )

        val corpo = objectMapper.writeValueAsString(
            RegisterRequest("Rafael Drummond", "rafael.drummond@noctua.com", "noctua123")
        )

        mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(corpo)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.email").value("rafael.drummond@noctua.com"))
            .andExpect(jsonPath("$.papel").value("DIRETOR_PROJETOS"))
            .andExpect(jsonPath("$.criado_em").value("2026-01-05T10:00:00Z"))
            .andExpect(jsonPath("$.senhaHash").doesNotExist())
            .andExpect(jsonPath("$.senha_hash").doesNotExist())
    }

    @Test
    fun `POST register recusa senha curta com 400 e lista a violacao por campo`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Ana","email":"ana@noctua.com","senha":"123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.erro").value("Requisicao invalida"))
            .andExpect(jsonPath("$.caminho").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.violacoes[0].campo").value("senha"))
    }

    @Test
    fun `POST register recusa e-mail malformado com 400`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Ana","email":"nao-e-email","senha":"senhaforte123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violacoes[0].campo").value("email"))
    }

    @Test
    fun `POST register recusa nome em branco com 400`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"   ","email":"ana@noctua.com","senha":"senhaforte123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violacoes[0].campo").value("nome"))
    }

    @Test
    fun `POST register recusa e-mail duplicado com 409`() {
        every { authService.registrar(any()) } throws
            EmailJaCadastradoException("Ja existe um usuario com esse e-mail")

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Ana","email":"ana@noctua.com","senha":"senhaforte123"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.erro").value("Conflito"))
    }

    @Test
    fun `POST login devolve o token com token_type e expires_in`() {
        every { authService.autenticar(any()) } returns LoginResponse(
            token = "jwt-emitido",
            expiresIn = 43_200L,
            usuario = UsuarioResponse(
                id = 1L,
                nome = "Rafael Drummond",
                email = "rafael.drummond@noctua.com",
                papel = "DIRETOR_PROJETOS",
                criadoEm = Instant.parse("2026-01-05T10:00:00Z")
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"rafael.drummond@noctua.com","senha":"noctua123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-emitido"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(43_200L))
            .andExpect(jsonPath("$.usuario.email").value("rafael.drummond@noctua.com"))
    }

    @Test
    fun `POST login com credenciais invalidas devolve 401`() {
        every { authService.autenticar(any()) } throws CredenciaisInvalidasException()

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"rafael.drummond@noctua.com","senha":"senha-errada"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos"))
    }

    @Test
    fun `POST login recusa corpo sem os campos obrigatorios`() {
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST login recusa JSON malformado com 400`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ isso nao e json }")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Requisicao invalida"))
    }
}
