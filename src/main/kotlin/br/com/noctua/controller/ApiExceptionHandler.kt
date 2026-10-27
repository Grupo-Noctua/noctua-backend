package br.com.noctua.controller

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun internalError(exception: Exception, request: HttpServletRequest): ProblemDetail {
        val errorId = UUID.randomUUID().toString()
        log.error("Falha na API: errorId={}, path={}", errorId, request.requestURI, exception)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Não foi possível consultar os dados do dashboard.",
        ).apply {
            title = "Erro interno"
            setProperty("errorId", errorId)
        }
    }
}
