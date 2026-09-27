package com.noctua.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// ---------------------------------------------------------------------
// Requisicoes
// ---------------------------------------------------------------------

data class RegisterRequest(
    @field:NotBlank(message = "nome e obrigatorio")
    @field:Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
    val nome: String,

    @field:NotBlank(message = "email e obrigatorio")
    @field:Email(message = "email invalido")
    @field:Size(max = 180, message = "email deve ter no maximo 180 caracteres")
    val email: String,

    @field:NotBlank(message = "senha e obrigatoria")
    @field:Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
    val senha: String
) {
    /** Normaliza o e-mail para evitar duplicidade por diferenca de caixa. */
    fun emailNormalizado(): String = email.trim().lowercase()
}

data class LoginRequest(
    @field:NotBlank(message = "email e obrigatorio")
    @field:Email(message = "email invalido")
    val email: String,

    @field:NotBlank(message = "senha e obrigatoria")
    val senha: String
) {
    fun emailNormalizado(): String = email.trim().lowercase()
}

// ---------------------------------------------------------------------
// Respostas
//
// Propriedades Kotlin em camelCase com @JsonProperty explicito sempre que o
// nome no fio (wire) usa snake_case.
// ---------------------------------------------------------------------

data class UsuarioResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val papel: String,
    @param:JsonProperty("criado_em") val criadoEm: Instant
)

data class LoginResponse(
    val token: String,
    @param:JsonProperty("token_type")
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val tokenType: String = "Bearer",
    @param:JsonProperty("expires_in") val expiresIn: Long,
    val usuario: UsuarioResponse
)
