package com.noctua.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Metadados do objeto no bucket S3 que originou o projeto.
 */
data class DocumentoOrigemResponse(
    val bucket: String,
    @param:JsonProperty("object_key") val objectKey: String,
    @param:JsonProperty("last_modified") val lastModified: Instant?
)

/**
 * Linha da tabela de projetos.
 *
 * `equipe` e uma lista de IDs de funcionarios (ex.: `["u1","u2","u3"]`) porque
 * e isso que o frontend consome para desenhar o stack de avatares. O
 * detalhamento ([ProjetoDetalheResponse]) traz os dados completos da equipe.
 */
data class ProjetoResumoResponse(
    val id: String,
    val nome: String,
    val cliente: String,
    val status: String,
    val progresso: Int,
    val orcamento: BigDecimal,
    val gasto: BigDecimal,
    val deadline: LocalDate,
    val fase: String,
    val equipe: List<String>
)

/**
 * Membro da equipe no detalhamento do projeto.
 *
 * `departamento` corresponde a area funcional (o frontend chama esse campo de
 * `departamento` no tipo `Employee`).
 */
data class MembroEquipeResponse(
    val id: String,
    val nome: String,
    val cargo: String,
    val departamento: String,
    val senioridade: String?
)

/** Detalhamento completo do projeto. */
data class ProjetoDetalheResponse(
    val id: String,
    val nome: String,
    val cliente: String,
    val status: String,
    val progresso: Int,
    val orcamento: BigDecimal,
    val gasto: BigDecimal,
    val deadline: LocalDate,
    val fase: String,
    @param:JsonProperty("documento_origem") val documentoOrigem: DocumentoOrigemResponse,
    val equipe: List<MembroEquipeResponse>,
    val tecnologias: List<String>
)
