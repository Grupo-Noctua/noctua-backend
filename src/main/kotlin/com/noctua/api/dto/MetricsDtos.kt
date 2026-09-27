package com.noctua.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Cards do topo da tela de Metricas.
 */
data class KpiResponse(
    @param:JsonProperty("total_funcionarios")
    val totalFuncionarios: Int,

    @param:JsonProperty("total_projetos_analisados")
    val totalProjetosAnalisados: Int,

    @param:JsonProperty("ferramenta_mais_utilizada")
    val ferramentaMaisUtilizada: String
)

/**
 * `funcionarios-tecnologia`: contagem de_FUNCIONARIOS por tecnologia.
 *
 * A soma das quantidades e 81 para 80 funcionarios porque um profissional
 * pode atuar em mais de uma tecnologia (relacionamento N:N). Portanto este
 * total NAO deve ser comparado com `total_funcionarios`.
 */
data class TecnologiaFuncionarioResponse(
    val tecnologia: String,
    val quantidade: Int
)

/** `funcionarios-area`: contagem por area funcional, ordenado de forma decrescente. */
data class AreaFuncionarioResponse(
    val area: String,
    val quantidade: Int
)

/**
 * `funcionarios-senioridade`: contagem por nivel.
 *
 * A resposta sempre contem os 6 niveis da hierarquia, na ordem
 * Estagiario -> Tech Lead, com quantidade zero quando nao ha ninguem.
 */
data class SenioridadeFuncionarioResponse(
    val senioridade: String,
    val quantidade: Int
)

/**
 * `projetos-tecnologia`: rosca de distribuicao de uso por tecnologia.
 *
 * [quantidade] e o somatorio dos niveis de adocao (0..3) da tecnologia nos
 * projetos — nao a contagem de projetos. Com 6 projetos cadastrados a
 * contagem de projetos jamais-passaria de 6, enquanto a rosca precisa
 * exibir valores de 7 a 11.
 *
 * [percentual] e a participacao de [quantidade] no total geral de usos.
 */
data class TecnologiaProjetoResponse(
    val tecnologia: String,
    val quantidade: Int,
    val percentual: Int
)
