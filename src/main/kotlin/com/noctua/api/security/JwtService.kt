package com.noctua.api.security

import com.noctua.api.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

/**
 * Emissao e validacao de tokens JWT (HMAC-SHA256).
 *
 * A assinatura e simetrica: o mesmo segredo valida e emite, o que dispensa
 * um par de chaves publica/privada. O segredo precisa ter ao menos 32 bytes
 * para o algoritmo escolhido — a construtor falha imediatamente caso
 * contrario, em vez de deixar o erro para o primeiro login.
 */
@Service
class JwtService(
    private val jwtProperties: JwtProperties
) {

    private companion object {
        val log = LoggerFactory.getLogger(JwtService::class.java)
        const val CLAIM_EMAIL = "email"
        const val CLAIM_NOME = "nome"
        const val CLAIM_PAPEL = "papel"
    }

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))

    /** Segundos de validade de um token recem-emitido. */
    val expiresInSeconds: Long get() = jwtProperties.expirationMinutes * 60

    fun gerarToken(usuarioId: Long, nome: String, email: String, papel: String): String {
        val agora = Instant.now()
        val expiracao = agora.plus(jwtProperties.expirationMinutes, ChronoUnit.MINUTES)
        return Jwts.builder()
            .subject(usuarioId.toString())
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_NOME, nome)
            .claim(CLAIM_PAPEL, papel)
            .issuer(jwtProperties.issuer)
            .issuedAt(Date.from(agora))
            .expiration(Date.from(expiracao))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    /**
     * @return os claims do token, ou `null` se o token for invalido,
     * adulterado ou estiver expirado. Nunca lanca: um token ruim deve
     * simplesmente nao autenticar a requisicao.
     */
    fun validarToken(token: String): Claims? = try {
        Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(jwtProperties.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (e: ExpiredJwtException) {
        log.debug("Token expirado: {}", e.message)
        null
    } catch (e: JwtException) {
        log.debug("Token invalido: {}", e.message)
        null
    } catch (e: IllegalArgumentException) {
        log.debug("Token malformado: {}", e.message)
        null
    }

    /** Identidade carregada no `SecurityContext` apos um login bem-sucedido. */
    data class Principal(
        val id: Long,
        val nome: String,
        val email: String,
        val papel: String
    ) {
        companion object {
            fun from(claims: Claims): Principal? {
                val id = claims.subject?.toLongOrNull() ?: return null
                val email = claims[CLAIM_EMAIL] as? String ?: return null
                return Principal(
                    id = id,
                    nome = claims[CLAIM_NOME] as? String ?: email.substringBefore('@'),
                    email = email,
                    papel = claims[CLAIM_PAPEL] as? String ?: "DIRETOR_PROJETOS"
                )
            }
        }
    }
}
