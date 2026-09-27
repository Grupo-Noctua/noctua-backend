package com.noctua.api.models

import java.time.LocalDate

data class Funcionario(
    val id: String,
    val nome: String,
    val cargo: String,
    val area: String,
    val senioridade: String?,
    val disponibilidade: Int,
    val dataIngresso: LocalDate,
    val localizacao: String,
    val tecnologias: List<String>
)

/** Resultado bruto de um agregado: rotulo textual + contagem. */
data class AgregadoRotuloQuantidade(
    val rotulo: String,
    val quantidade: Int
)
