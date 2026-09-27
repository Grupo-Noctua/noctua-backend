package com.noctua.api.security

import com.noctua.api.config.JwtProperties
import io.jsonwebtoken.security.WeakKeyException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JwtService - emissao e validacao de tokens")
class JwtServiceTest {

    private val segredo = "noctua-teste-chave-secreta-com-32-bytes-ou-mais"

    private lateinit var service: JwtService

    @BeforeEach
    fun setUp() {
        service = JwtService(JwtProperties(secret = segredo, expirationMinutes = 60, issuer = "noctua-api"))
    }

    @Test
    fun `gera um token com tres partes`() {
        val token = service.gerarToken(1L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")

        assertThat(token.split(".")).hasSize(3)
    }

    @Test
    fun `recupera as claims do token emitido`() {
        val token = service.gerarToken(7L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")

        val claims = service.validarToken(token)

        assertThat(claims).isNotNull()
        assertThat(claims!!.subject).isEqualTo("7")
        assertThat(claims["email"]).isEqualTo("rafael@noctua.com")
        assertThat(claims["nome"]).isEqualTo("Rafael Drummond")
        assertThat(claims["papel"]).isEqualTo("DIRETOR_PROJETOS")
    }

    @Test
    fun `constrói o principal a partir das claims`() {
        val token = service.gerarToken(42L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")

        val principal = JwtService.Principal.from(service.validarToken(token)!!)

        assertThat(principal).isNotNull()
        assertThat(principal!!.id).isEqualTo(42L)
        assertThat(principal.nome).isEqualTo("Rafael Drummond")
        assertThat(principal.email).isEqualTo("rafael@noctua.com")
        assertThat(principal.papel).isEqualTo("DIRETOR_PROJETOS")
    }

    @Test
    fun `rejeita token adulterado no payload`() {
        val token = service.gerarToken(1L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")
        val partes = token.split(".")
        val payloadAdulterado = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"1","email":"intruso@atacante.com","papel":"ADMIN"}""".toByteArray()
        )

        val tokenAdulterado = "${partes[0]}.$payloadAdulterado.${partes[2]}"

        assertThat(service.validarToken(tokenAdulterado)).isNull()
    }

    @Test
    fun `rejeita token assinado com outro segredo`() {
        val outroServico = JwtService(JwtProperties(secret = "outro-segredo-totalmente-diferente-32b"))
        val tokenEstrangeiro = outroServico.gerarToken(1L, "Intruso", "intruso@atacante.com", "ADMIN")

        assertThat(service.validarToken(tokenEstrangeiro)).isNull()
    }

    @Test
    fun `rejeita token expirado sem lancar excecao`() {
        val expirado = JwtService(
            JwtProperties(secret = segredo, expirationMinutes = -10, issuer = "noctua-api")
        )
        val token = expirado.gerarToken(1L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")

        assertThat(service.validarToken(token)).isNull()
    }

    @Test
    fun `rejeita token de issuer diferente`() {
        val outroIssuer = JwtService(JwtProperties(secret = segredo, expirationMinutes = 60, issuer = "outro-sistema"))
        val token = outroIssuer.gerarToken(1L, "Rafael Drummond", "rafael@noctua.com", "DIRETOR_PROJETOS")

        assertThat(service.validarToken(token)).isNull()
    }

    @Test
    fun `devolve null em vez de lancar para entrada totalmente invalida`() {
        listOf("", "   ", "abc", "a.b.c", "....").forEach { entrada ->
            assertThat(service.validarToken(entrada)).`as`("entrada: '$entrada'").isNull()
        }
    }

    @Test
    fun `expõe a validade em segundos a partir das configuracoes`() {
        assertThat(service.expiresInSeconds).isEqualTo(3600L)
    }

    @Test
    fun `falha na inicializacao se o segredo tiver menos de 32 bytes`() {
        // HMAC-SHA256 exige uma chave de ao menos 256 bits; o erro deve
        // aparecer na inicializacao, e nao no primeiro login.
        assertThatThrownBy {
            JwtService(JwtProperties(secret = "curto"))
        }.isInstanceOf(WeakKeyException::class.java)
    }
}
