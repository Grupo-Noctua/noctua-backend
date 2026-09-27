package com.noctua.api.services

import com.noctua.api.dto.CredenciaisInvalidasException
import com.noctua.api.dto.EmailJaCadastradoException
import com.noctua.api.dto.LoginRequest
import com.noctua.api.dto.LoginResponse
import com.noctua.api.dto.RecursoNaoEncontradoException
import com.noctua.api.dto.RegisterRequest
import com.noctua.api.dto.UsuarioResponse
import com.noctua.api.models.Usuario
import com.noctua.api.repositories.UsuarioRepository
import com.noctua.api.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Cadastro, autenticacao e consulta de perfil.
 *
 * Nao ha demarcacao transacional: cada operacao corresponde a uma unica
 * instrucao SQL. A unicidade do e-mail e garantida pela constraint `UNIQUE`
 * em `usuario.email`, e nao por verificacao previa — assim duas requisoes
 * simultaneas com o mesmo e-mail nao conseguem criar duplicatas.
 */
@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    private companion object {
        val log = LoggerFactory.getLogger(AuthService::class.java)
        const val PAPEL_PADRAO = "DIRETOR_PROJETOS"
    }

    fun registrar(request: RegisterRequest): UsuarioResponse {
        val email = request.emailNormalizado()

        if (usuarioRepository.existePorEmail(email)) {
            throw EmailJaCadastradoException("Ja existe um usuario cadastrado com o e-mail $email")
        }

        // A senha nunca e persistida em claro: apenas o digest BCrypt vai
        // para o banco.
        val senhaHash = passwordEncoder.encode(request.senha)
        val usuario = usuarioRepository.criar(
            nome = request.nome.trim(),
            email = email,
            senhaHash = senhaHash,
            papel = PAPEL_PADRAO
        )

        log.info("Usuario registrado: id={} email={}", usuario.id, usuario.email)
        return usuario.paraResponse()
    }

    fun autenticar(request: LoginRequest): LoginResponse {
        val email = request.emailNormalizado()
        val usuario = usuarioRepository.buscarPorEmail(email)

        // A mesma excecao para e-mail inexistente e senha errada, para nao
        // permitir enumeracao de contas.
        if (usuario == null || !passwordEncoder.matches(request.senha, usuario.senhaHash)) {
            log.warn("Tentativa de login negada para o e-mail {}", email)
            throw CredenciaisInvalidasException()
        }

        val token = jwtService.gerarToken(
            usuarioId = usuario.id,
            nome = usuario.nome,
            email = usuario.email,
            papel = usuario.papel
        )

        return LoginResponse(
            token = token,
            expiresIn = jwtService.expiresInSeconds,
            usuario = usuario.paraResponse()
        )
    }

    /** Perfil do usuario autenticado, relido do banco para nao confiar no token. */
    fun perfil(usuarioId: Long): UsuarioResponse {
        val usuario = usuarioRepository.buscarPorId(usuarioId)
            ?: throw RecursoNaoEncontradoException("Usuario $usuarioId nao encontrado")
        return usuario.paraResponse()
    }

    private fun Usuario.paraResponse() = UsuarioResponse(
        id = id,
        nome = nome,
        email = email,
        papel = papel,
        criadoEm = criadoEm
    )
}
