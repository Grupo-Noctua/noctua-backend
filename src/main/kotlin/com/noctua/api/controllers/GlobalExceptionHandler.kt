package com.noctua.api.controllers

import com.noctua.api.dto.ApiError
import com.noctua.api.dto.ApiException
import com.noctua.api.dto.ViolacaoCampo
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * Traduz excecoes em respostas [ApiError] com o mesmo formato em toda a API.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private companion object {
        val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ApiError> =
        resposta(ex.status, ex.erro, ex.message ?: "Erro nao especificado", request.requestURI)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiError> {
        val violacoes = ex.bindingResult.fieldErrors.map { erro ->
            ViolacaoCampo(
                campo = erro.field,
                mensagem = erro.defaultMessage ?: "valor invalido"
            )
        }
        return resposta(
            HttpStatus.BAD_REQUEST.value(),
            "Requisicao invalida",
            violacoes.firstOrNull()?.mensagem ?: "Payload invalido",
            request.requestURI,
            violacoes
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ApiError> =
        resposta(
            HttpStatus.BAD_REQUEST.value(),
            "Requisicao invalida",
            "Corpo da requisicao ausente ou com formato invalido",
            request.requestURI
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        log.error("Erro inesperado em {}: {}", request.requestURI, ex.message, ex)
        return resposta(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Erro interno",
            "Ocorreu um erro inesperado. Tente novamente mais tarde.",
            request.requestURI
        )
    }

    private fun resposta(
        status: Int,
        erro: String,
        mensagem: String,
        caminho: String,
        violacoes: List<ViolacaoCampo>? = null
    ): ResponseEntity<ApiError> = ResponseEntity.status(status).body(
        ApiError(
            timestamp = Instant.now(),
            status = status,
            erro = erro,
            mensagem = mensagem,
            caminho = caminho,
            violacoes = violacoes
        )
    )
}
