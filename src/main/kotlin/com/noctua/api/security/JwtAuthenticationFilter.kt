package com.noctua.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Le `Authorization: Bearer <token>`, valida a assinatura e publica a
 * identidade no `SecurityContext`.
 *
 * Requisicoes sem token ou com token invalido seguem o fluxo sem autenticacao
 * — quem decide bloquear e o `SecurityConfig`, que devolve 401 nas rotas
 * protegidas. Isso evita duplicar a escrita de respostas 401 entre o filtro e
 * o entry point.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.extractBearerToken()

        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            jwtService.validarToken(token)?.let { claims ->
                JwtService.Principal.from(claims)?.let { principal ->
                    val autenticacao = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${principal.papel}"))
                    ).apply {
                        details = WebAuthenticationDetailsSource().buildDetails(request)
                    }
                    SecurityContextHolder.getContext().authentication = autenticacao
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun HttpServletRequest.extractBearerToken(): String? {
        val cabecalho = getHeader("Authorization") ?: return null
        if (!cabecalho.startsWith(PREFIXO_BEARER, ignoreCase = true)) return null
        return cabecalho.removePrefix(PREFIXO_BEARER).trim().ifEmpty { null }
    }

    private companion object {
        const val PREFIXO_BEARER = "Bearer "
    }
}
