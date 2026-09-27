package com.noctua.api.repositories

import com.noctua.api.models.AgregadoRotuloQuantidade
import org.springframework.stereotype.Repository
import java.sql.Connection
import javax.sql.DataSource

/**
 * Implementacao JDBC nativa de [MetricasRepository].
 *
 * Cada agregado vira exatamente uma consulta agregada no banco; nenhum
 * calculo de negocio acontece aqui. O unico calculo do servico e o
 * percentual da rosca, que depende do total e por isso fica em Kotlin.
 */
@Repository
class MetricasJdbcRepository(
    private val dataSource: DataSource
) : MetricasRepository {

    override fun contarFuncionarios(): Int =
        escalar("SELECT COUNT(*) FROM funcionario")

    override fun contarProjetos(): Int =
        escalar("SELECT COUNT(*) FROM projeto")

    private fun escalar(sql: String): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmts ->
                stmts.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    override fun usosPorTecnologia(): List<AgregadoRotuloQuantidade> =
        consultar(
            """
            SELECT t.nome AS rotulo, SUM(pt.nivel_uso)::int AS quantidade
            FROM projeto_tecnologia pt
            JOIN tecnologia t ON t.id = pt.tecnologia_id
            GROUP BY t.id, t.nome
            ORDER BY SUM(pt.nivel_uso) DESC, t.nome ASC
            """.trimIndent()
        )

    override fun tecnologiaMaisUtilizada(): String? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT t.nome
                FROM projeto_tecnologia pt
                JOIN tecnologia t ON t.id = pt.tecnologia_id
                GROUP BY t.id, t.nome
                ORDER BY SUM(pt.nivel_uso) DESC, t.nome ASC
                LIMIT 1
                """.trimIndent()
            ).use { stmts ->
                stmts.executeQuery().use { rs -> if (rs.next()) rs.getString("nome") else null }
            }
        }

    override fun vinculosPorTecnologia(): List<AgregadoRotuloQuantidade> =
        consultar(
            """
            SELECT t.nome AS rotulo, COUNT(*)::int AS quantidade
            FROM funcionario_tecnologia ft
            JOIN tecnologia t ON t.id = ft.tecnologia_id
            GROUP BY t.id, t.nome
            ORDER BY t.id ASC
            """.trimIndent()
        )

    override fun funcionariosPorArea(): List<AgregadoRotuloQuantidade> =
        consultar(
            """
            SELECT a.nome AS rotulo, COUNT(f.id)::int AS quantidade
            FROM area a
            LEFT JOIN funcionario f ON f.area_id = a.id
            GROUP BY a.id, a.nome
            ORDER BY COUNT(f.id) DESC, a.nome ASC
            """.trimIndent()
        )

    /**
     * O `LEFT JOIN` a partir de `senioridade` e o que garante o
     * preenchimento zero: um nivel sem nenhuma pessoa aparece com
     * quantidade 0, preservando os 6 pontos da hierarquia.
     */
    override fun funcionariosPorSenioridade(): List<AgregadoRotuloQuantidade> =
        consultar(
            """
            SELECT s.nome AS rotulo, COALESCE(contagem.total, 0)::int AS quantidade
            FROM senioridade s
            LEFT JOIN (
                SELECT senioridade_id, COUNT(*) AS total
                FROM funcionario
                GROUP BY senioridade_id
            ) contagem ON contagem.senioridade_id = s.id
            ORDER BY s.ordem ASC
            """.trimIndent()
        )

    private fun consultar(sql: String): List<AgregadoRotuloQuantidade> =
        dataSource.connection.use { conn -> consultarAgregado(conn, sql) }

    private fun consultarAgregado(
        conn: Connection,
        sql: String
    ): List<AgregadoRotuloQuantidade> =
        conn.prepareStatement(sql).use { stmts ->
            stmts.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AgregadoRotuloQuantidade(
                                rotulo = rs.getString("rotulo"),
                                quantidade = rs.getInt("quantidade")
                            )
                        )
                    }
                }
            }
        }
}
