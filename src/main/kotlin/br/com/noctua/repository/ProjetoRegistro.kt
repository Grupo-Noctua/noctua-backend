package br.com.noctua.repository

import br.com.noctua.dto.ProfissionalRecomendadoDTO
import br.com.noctua.dto.ProjetoDTO

/** Uma ocorrência SQL; os identificadores preservam o contexto de vaga/matching. */
data class ProjetoRegistro(
    val projeto: ProjetoDTO,
    val idMatchingExecucaoVaga: Long?,
    val idCandidatoRanqueado: Long?,
    val profissional: ProfissionalRecomendadoDTO?,
)
