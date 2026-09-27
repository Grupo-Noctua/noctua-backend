package com.noctua.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Funcionario do quadro de equipe.
 *
 * `departamento` e a area funcional; `disponibilidade` e o percentual de
 * tempo livre (0 a 100), nao percentual de alocacao.
 */
data class FuncionarioResponse(
    val id: String,
    val nome: String,
    val cargo: String,
    val departamento: String,
    @param:JsonInclude(JsonInclude.Include.NON_NULL) val senioridade: String?,
    @param:JsonInclude(JsonInclude.Include.NON_NULL) val tecnologia: String?,
    val disponibilidade: Int,
    @param:JsonProperty("data_ingresso") val dataIngresso: LocalDate,
    val localizacao: String
)
