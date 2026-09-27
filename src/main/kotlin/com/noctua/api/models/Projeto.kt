package com.noctua.api.models

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Metadados do documento que originou o projeto no bucket S3.
 *
 * A listagem de projetos expoe apenas estes dados de proveniencia; nao ha
 * cliente S3 na aplicacao.
 */
data class DocumentoOrigem(
    val bucket: String,
    val objectKey: String,
    val lastModified: Instant?
)

data class Projeto(
    val id: String,
    val nome: String,
    val cliente: String,
    val status: StatusProjeto,
    val progresso: Int,
    val orcamento: BigDecimal,
    val gasto: BigDecimal,
    val deadline: LocalDate,
    val fase: String,
    val documentoOrigem: DocumentoOrigem
)

/** Vinculo de alocacao entre projeto e funcionario. */
data class Alocacao(
    val projetoId: String,
    val funcionarioId: String
)

/**
 * Membro da equipe com os dados exibidos no detalhamento do projeto.
 * O grafico de avatares da listagem usa apenas [id]; o drawer usa o
 * restante.
 */
data class MembroEquipe(
    val id: String,
    val nome: String,
    val cargo: String,
    val area: String,
    val senioridade: String?
)

/** Tecnologia utilizada por um projeto e o peso de sua adocao. */
data class TecnologiaProjeto(
    val nome: String,
    val nivelUso: Int
)
