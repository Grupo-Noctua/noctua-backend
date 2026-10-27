package br.com.noctua.service

import br.com.noctua.repository.DashboardAthenaRepository
import br.com.noctua.repository.DashboardJdbcRepository
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val jdbc: DashboardJdbcRepository,
    private val athena: DashboardAthenaRepository,
) {
    fun totalFuncionarios() = athena.totalFuncionarios()
    fun funcionariosPorTecnologia() = athena.funcionariosPorTecnologia()
    fun funcionariosPorSenioridade() = athena.funcionariosPorSenioridade()
    fun funcionariosPorArea() = athena.funcionariosPorArea()
    fun totalProjetos() = jdbc.totalProjetos()
    fun ferramentasMaisUtilizadas() = jdbc.ferramentasMaisUtilizadas()
    fun projetosPorTecnologia() = jdbc.projetosPorTecnologia()

    fun projetos() = jdbc.projetos()
        .groupBy { it.projeto.idProcessamento }
        .values.map { registros ->
            // Sem regra para escolher entre documentos conflitantes, falhar evita perda silenciosa.
            check(registros.map { it.projeto.idProjeto }.distinct().size == 1) {
                "Mais de um Documento_Projeto associado ao mesmo Processamento"
            }
            registros.first().projeto.copy(
                profissionaisRecomendados = registros
                    .sortedWith(compareBy(
                        { it.idMatchingExecucaoVaga },
                        { it.profissional?.posicaoRanking },
                        { it.idCandidatoRanqueado },
                    ))
                    .mapNotNull { it.profissional },
            )
        }
}
