package br.com.noctua.repository

import br.com.noctua.dto.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class DashboardJdbcRepository(private val jdbc: JdbcTemplate) {
    fun totalProjetos() = TotalDTO(
        requireNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM Processamento", Long::class.java)),
    )

    fun ferramentasMaisUtilizadas(): List<FerramentaDTO> = jdbc.query(
        """
        WITH contagem AS (
            SELECT vc.nome_competencia AS ferramenta,
                   COUNT(DISTINCT vc.fk_vaga_processada) AS quantidade
            FROM Vaga_Competencia vc
            GROUP BY vc.nome_competencia
        )
        SELECT ferramenta, quantidade FROM contagem
        WHERE quantidade = (SELECT MAX(quantidade) FROM contagem)
        ORDER BY ferramenta
        """.trimIndent(),
    ) { rs, _ -> FerramentaDTO(rs.getString("ferramenta"), rs.getLong("quantidade")) }

    fun projetosPorTecnologia(): List<TecnologiaDTO> = jdbc.query(
        """
        SELECT vc.nome_competencia AS tecnologia,
               COUNT(DISTINCT vp.fk_processamento) AS quantidade
        FROM Vaga_Competencia vc
        INNER JOIN Vaga_Processada vp ON vp.id_vaga_processada = vc.fk_vaga_processada
        GROUP BY vc.nome_competencia
        ORDER BY quantidade DESC, tecnologia
        """.trimIndent(),
    ) { rs, _ -> TecnologiaDTO(rs.getString("tecnologia"), rs.getLong("quantidade")) }

    fun projetos(): List<ProjetoRegistro> = jdbc.query(
        """
        SELECT p.id_processamento,
               dp.id_documento, dp.nome_projeto, dp.cliente, dp.gestor_responsavel,
               dp.data_inicio_prevista, dp.duracao_estimada, dp.orcamento,
               dp.descricao, dp.objetivo_geral,
               mev.id_matching_execucao_vaga, mev.titulo_vaga,
               cr.id_candidato_ranqueado, cr.id_profissional_externo, cr.nome,
               cr.senioridade, cr.posicao_ranking, cr.score_total, cr.justificativa_curta
        FROM Processamento p
        LEFT JOIN Documento_Projeto dp ON dp.fk_processamento = p.id_processamento
        LEFT JOIN Matching_Execucao me ON me.fk_processamento = p.id_processamento
        LEFT JOIN Matching_Execucao_Vaga mev ON mev.fk_matching_execucao = me.id_matching_execucao
        LEFT JOIN Candidato_Ranqueado cr ON cr.fk_matching_execucao_vaga = mev.id_matching_execucao_vaga
        ORDER BY p.id_processamento, dp.id_documento, mev.id_matching_execucao_vaga,
                 cr.posicao_ranking, cr.id_candidato_ranqueado
        """.trimIndent(),
    ) { rs, _ ->
        val idCandidato = rs.nullableLong("id_candidato_ranqueado")
        ProjetoRegistro(
            projeto = ProjetoDTO(
                idProcessamento = rs.getLong("id_processamento"),
                idProjeto = rs.nullableLong("id_documento"),
                nomeProjeto = rs.getString("nome_projeto"),
                cliente = rs.getString("cliente"),
                gestorResponsavel = rs.getString("gestor_responsavel"),
                dataInicioPrevista = rs.getDate("data_inicio_prevista")?.toLocalDate(),
                duracaoEstimada = rs.getString("duracao_estimada"),
                orcamento = rs.getBigDecimal("orcamento"),
                descricao = rs.getString("descricao"),
                objetivoGeral = rs.getString("objetivo_geral"),
            ),
            idMatchingExecucaoVaga = rs.nullableLong("id_matching_execucao_vaga"),
            idCandidatoRanqueado = idCandidato,
            profissional = if (idCandidato == null) null else ProfissionalRecomendadoDTO(
                idProfissional = rs.getString("id_profissional_externo"),
                nome = rs.getString("nome"),
                senioridade = rs.getString("senioridade"),
                vaga = rs.getString("titulo_vaga"),
                posicaoRanking = rs.nullableLong("posicao_ranking")?.let(Math::toIntExact),
                scoreTotal = rs.getBigDecimal("score_total"),
                justificativa = rs.getString("justificativa_curta"),
            ),
        )
    }

    private fun ResultSet.nullableLong(column: String): Long? =
        getLong(column).let { if (wasNull()) null else it }
}
