package com.noctua.api.repositories

import com.noctua.api.models.Alocacao
import com.noctua.api.models.DocumentoOrigem
import com.noctua.api.models.MembroEquipe
import com.noctua.api.models.Projeto
import com.noctua.api.models.StatusProjeto
import com.noctua.api.models.TecnologiaProjeto
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import javax.sql.DataSource

/**
 * Implementacao JDBC nativa de [ProjetoRepository].
 *
 * A listagem resolve a equipe de todos os projetos em uma unica consulta e
 * agrupa em memoria, em vez de disparar uma consulta por projeto (N+1).
 */
@Repository
class ProjetoJdbcRepository(
    private val dataSource: DataSource
) : ProjetoRepository {

    private companion object {
        const val COLUNAS_PROJETO = """
            p.id, p.nome, p.cliente, p.status, p.progresso,
            p.orcamento, p.gasto, p.deadline, p.fase,
            p.s3_bucket, p.s3_object_key, p.s3_last_modified
        """

        val MAPER_PROJETO: RowMapper<Projeto> = RowMapper { rs, _ ->
            Projeto(
                id = rs.getString("id"),
                nome = rs.getString("nome"),
                cliente = rs.getString("cliente"),
                status = StatusProjeto.fromRotulo(rs.getString("status"))
                    ?: StatusProjeto.EM_DIA,
                progresso = rs.getInt("progresso"),
                orcamento = rs.getBigDecimal("orcamento"),
                gasto = rs.getBigDecimal("gasto"),
                deadline = rs.getDate("deadline").toLocalDate(),
                fase = rs.getString("fase"),
                documentoOrigem = DocumentoOrigem(
                    bucket = rs.getString("s3_bucket"),
                    objectKey = rs.getString("s3_object_key"),
                    lastModified = rs.getObject("s3_last_modified", OffsetDateTime::class.java)
                        ?.toInstant()
                )
            )
        }
    }

    override fun listarTodos(): List<Projeto> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $COLUNAS_PROJETO FROM projeto p ORDER BY p.id ASC"
            ).use { stmts ->
                stmts.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(MAPER_PROJETO.mapRow(rs, 0)!!)
                        }
                    }
                }
            }
        }

    override fun buscarPorId(id: String): Projeto? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $COLUNAS_PROJETO FROM projeto p WHERE p.id = ?"
            ).use { stmts ->
                stmts.setString(1, id)
                stmts.executeQuery().use { rs ->
                    if (rs.next()) MAPER_PROJETO.mapRow(rs, 0) else null
                }
            }
        }

    override fun listarAlocacoes(): List<Alocacao> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT pf.projeto_id, pf.funcionario_id
                FROM projeto_funcionario pf
                ORDER BY pf.projeto_id ASC, pf.funcionario_id ASC
                """.trimIndent()
            ).use { stmts ->
                stmts.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                Alocacao(
                                    projetoId = rs.getString("projeto_id"),
                                    funcionarioId = rs.getString("funcionario_id")
                                )
                            )
                        }
                    }
                }
            }
        }

    override fun listarEquipe(projetoId: String): List<MembroEquipe> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT f.id, f.nome, f.cargo, a.nome AS area, s.nome AS senioridade
                FROM projeto_funcionario pf
                JOIN funcionario f ON f.id = pf.funcionario_id
                JOIN area a ON a.id = f.area_id
                LEFT JOIN senioridade s ON s.id = f.senioridade_id
                WHERE pf.projeto_id = ?
                ORDER BY f.nome ASC
                """.trimIndent()
            ).use { stmts ->
                stmts.setString(1, projetoId)
                stmts.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                MembroEquipe(
                                    id = rs.getString("id"),
                                    nome = rs.getString("nome"),
                                    cargo = rs.getString("cargo"),
                                    area = rs.getString("area"),
                                    senioridade = rs.getString("senioridade")
                                )
                            )
                        }
                    }
                }
            }
        }

    override fun listarTecnologias(projetoId: String): List<TecnologiaProjeto> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT t.nome, pt.nivel_uso
                FROM projeto_tecnologia pt
                JOIN tecnologia t ON t.id = pt.tecnologia_id
                WHERE pt.projeto_id = ?
                ORDER BY pt.nivel_uso DESC, t.nome ASC
                """.trimIndent()
            ).use { stmts ->
                stmts.setString(1, projetoId)
                stmts.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                TecnologiaProjeto(
                                    nome = rs.getString("nome"),
                                    nivelUso = rs.getInt("nivel_uso")
                                )
                            )
                        }
                    }
                }
            }
        }
}
