package com.noctua.api.dto

import java.time.Instant

/**
 * Corpo padronizado de erro da API.
 *
 * ```json
 * { "timestamp": "...", "status": 400, "erro": "Requisicao invalida",
 *   "mensagem": "email invalido", "caminho": "/api/v1/auth/register",
 *   "violacoes": [{ "campo": "email", "mensagem": "email invalido" }] }
 * ```
 */
data class ApiError(
    val timestamp: Instant,
    val status: Int,
    val erro: String,
    val mensagem: String,
    val caminho: String,
    val violacoes: List<ViolacaoCampo>? = null
)

data class ViolacaoCampo(
    val campo: String,
    val mensagem: String
)

/** Base das falhas de negocio traduzidas para um status HTTP. */
sealed class ApiException(
    val status: Int,
    val erro: String,
    message: String
) : RuntimeException(message)

class RecursoNaoEncontradoException(mensagem: String) :
    ApiException(404, "Recurso nao encontrado", mensagem)

class EmailJaCadastradoException(mensagem: String) :
    ApiException(409, "Conflito", mensagem)

class CredenciaisInvalidasException :
    ApiException(401, "Nao autorizado", "E-mail ou senha invalidos")

class RequisicaoInvalidaException(mensagem: String) :
    ApiException(400, "Requisicao invalida", mensagem)
