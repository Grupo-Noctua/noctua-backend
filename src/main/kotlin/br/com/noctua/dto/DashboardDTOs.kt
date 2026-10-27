package br.com.noctua.dto

import java.math.BigDecimal
import java.time.LocalDate

data class TotalDTO(val total: Long)
data class FerramentaDTO(val ferramenta: String?, val quantidade: Long)
data class TecnologiaDTO(val tecnologia: String?, val quantidade: Long)
data class SenioridadeDTO(val senioridade: String?, val quantidade: Long)
data class AreaDTO(val area: String?, val quantidade: Long)

data class ProjetoDTO(
    val idProcessamento: Long,
    val idProjeto: Long?,
    val nomeProjeto: String?,
    val cliente: String?,
    val gestorResponsavel: String?,
    val dataInicioPrevista: LocalDate?,
    val duracaoEstimada: String?,
    val orcamento: BigDecimal?,
    val descricao: String?,
    val objetivoGeral: String?,
    val profissionaisRecomendados: List<ProfissionalRecomendadoDTO> = emptyList(),
)

data class ProfissionalRecomendadoDTO(
    val idProfissional: String?,
    val nome: String?,
    val senioridade: String?,
    val vaga: String?,
    val posicaoRanking: Int?,
    val scoreTotal: BigDecimal?,
    val justificativa: String?,
)
