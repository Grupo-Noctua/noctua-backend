package com.noctua.api.services

import com.noctua.api.dto.CredenciaisInvalidasException
import com.noctua.api.dto.EmailJaCadastradoException
import com.noctua.api.dto.LoginRequest
import com.noctua.api.dto.RecursoNaoEncontradoException
import com.noctua.api.dto.RegisterRequest
import com.noctua.api.models.Usuario
import com.noctua.api.repositories.UsuarioRepository
import com.noctua.api.security.JwtService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

@ExtendWith(MockKExtension::class)
@DisplayName("AuthService - cadastro, login e perfil")
class AuthServiceTest {

    private val encoder: PasswordEncoder = BCryptPasswordEncoder(12)

    @MockK
    private lateinit var usuarioRepository: UsuarioRepository

    @MockK
    private lateinit var jwtService: JwtService

    private lateinit var service: AuthService

    private val usuarioValido = Usuario(
        id = 1L,
        nome = "Rafael Drummond",
        email = "rafael.drummond@noctua.com",
        senhaHash = encoder.encode("noctua123"),
        papel = "DIRETOR_PROJETOS",
        criadoEm = Instant.parse("2026-01-05T10:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        service = AuthService(usuarioRepository, encoder, jwtService)
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    inner class Registrar {

        @Test
        fun `persiste a senha como hash BCrypt e nunca em texto puro`() {
            every { usuarioRepository.existePorEmail("nova@noctua.com") } returns false

            val hashCaptor = slot<String>()
            every {
                usuarioRepository.criar(any(), any(), capture(hashCaptor), any())
            } returns usuarioValido.copy(nome = "Nova Usuario", email = "nova@noctua.com")

            service.registrar(RegisterRequest("Nova Usuario", "nova@noctua.com", "senhaforte123"))

            assertThat(hashCaptor.captured).isNotEqualTo("senhaforte123")
            assertThat(hashCaptor.captured).startsWith("\$2a\$")
            assertThat(encoder.matches("senhaforte123", hashCaptor.captured)).isTrue()
        }

        @Test
        fun `normaliza o e-mail para minusculas e sem espacos nas pontas`() {
            every { usuarioRepository.existePorEmail("nova@noctua.com") } returns false
            every { usuarioRepository.criar(any(), any(), any(), any()) } returns usuarioValido

            service.registrar(RegisterRequest("Nova", "  NOVA@Noctua.com  ", "senhaforte123"))

            verify { usuarioRepository.criar("Nova", "nova@noctua.com", any(), "DIRETOR_PROJETOS") }
        }

        @Test
        fun `recusa cadastro com e-mail ja existente`() {
            every { usuarioRepository.existePorEmail("rafael.drummond@noctua.com") } returns true

            assertThatThrownBy {
                service.registrar(
                    RegisterRequest("Rafael Drummond", "rafael.drummond@noctua.com", "senhaforte123")
                )
            }
                .isInstanceOf(EmailJaCadastradoException::class.java)

            verify(exactly = 0) { usuarioRepository.criar(any(), any(), any(), any()) }
        }

        @Test
        fun `devolve o perfil com papel padrao e dados vindos do banco`() {
            every { usuarioRepository.existePorEmail(any()) } returns false
            every { usuarioRepository.criar(any(), any(), any(), any()) } returns usuarioValido

            val resposta = service.registrar(
                RegisterRequest("Rafael Drummond", "rafael.drummond@noctua.com", "senhaforte123")
            )

            assertThat(resposta.id).isEqualTo(1L)
            assertThat(resposta.email).isEqualTo("rafael.drummond@noctua.com")
            assertThat(resposta.papel).isEqualTo("DIRETOR_PROJETOS")
            assertThat(resposta.criadoEm).isEqualTo(Instant.parse("2026-01-05T10:00:00Z"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    inner class Login {

        @Test
        fun `devolve o token e o perfil quando as credenciais conferem`() {
            every { usuarioRepository.buscarPorEmail("rafael.drummond@noctua.com") } returns usuarioValido
            every { jwtService.gerarToken(1L, "Rafael Drummond", usuarioValido.email, "DIRETOR_PROJETOS") } returns "jwt-emitido"
            every { jwtService.expiresInSeconds } returns 43_200L

            val resposta = service.autenticar(
                LoginRequest("rafael.drummond@noctua.com", "noctua123")
            )

            assertThat(resposta.token).isEqualTo("jwt-emitido")
            assertThat(resposta.tokenType).isEqualTo("Bearer")
            assertThat(resposta.expiresIn).isEqualTo(43_200L)
            assertThat(resposta.usuario.email).isEqualTo("rafael.drummond@noctua.com")
        }

        @Test
        fun `recusa senha incorreta`() {
            every { usuarioRepository.buscarPorEmail("rafael.drummond@noctua.com") } returns usuarioValido

            assertThatThrownBy {
                service.autenticar(LoginRequest("rafael.drummond@noctua.com", "senha-errada"))
            }
                .isInstanceOf(CredenciaisInvalidasException::class.java)

            verify(exactly = 0) { jwtService.gerarToken(any(), any(), any(), any()) }
        }

        @Test
        fun `recusa e-mail inexistente com a mesma excecao, evitando enumeracao de contas`() {
            every { usuarioRepository.buscarPorEmail("ninguem@noctua.com") } returns null

            assertThatThrownBy {
                service.autenticar(LoginRequest("ninguem@noctua.com", "noctua123"))
            }
                .isInstanceOf(CredenciaisInvalidasException::class.java)
                .hasMessage("E-mail ou senha invalidos")
        }

        @Test
        fun `normaliza o e-mail antes de consultar o repositorio`() {
            every { usuarioRepository.buscarPorEmail("rafael.drummond@noctua.com") } returns usuarioValido
            every { jwtService.gerarToken(any(), any(), any(), any()) } returns "jwt-emitido"
            every { jwtService.expiresInSeconds } returns 43_200L

            service.autenticar(LoginRequest("  Rafael.Drummond@Noctua.COM ", "noctua123"))

            verify { usuarioRepository.buscarPorEmail("rafael.drummond@noctua.com") }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me")
    inner class Perfil {

        @Test
        fun `relê o usuario do banco em vez de confiar no token`() {
            every { usuarioRepository.buscarPorId(1L) } returns usuarioValido

            val perfil = service.perfil(1L)

            assertThat(perfil.id).isEqualTo(1L)
            assertThat(perfil.nome).isEqualTo("Rafael Drummond")
            verify { usuarioRepository.buscarPorId(1L) }
        }

        @Test
        fun `sinaliza 404 quando o usuario do token nao existe mais`() {
            every { usuarioRepository.buscarPorId(99L) } returns null

            assertThatThrownBy { service.perfil(99L) }
                .isInstanceOf(RecursoNaoEncontradoException::class.java)
        }
    }
}
