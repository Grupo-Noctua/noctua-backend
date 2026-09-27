package com.noctua.api.repositories

import com.noctua.api.models.Funcionario
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import javax.sql.DataSource

/**
 * Implementacao JDBC nativa de [FuncionarioRepository].
 *
 * As tecnologias de cada profissional chegam em uma segunda consulta e sao
 * agrupadas em memoria, evitando N+1 na listagem.
 */
@Repository
class FuncionarioJdbcRepository(
    private val dataSource: DataSource
) : FuncionarioRepository {

    private companion object {
        val MAPER_BASE: RowMapper<Funcionario> = RowMapper { rs, _ ->
            Funcionario(
                id = rs.getString("id"),
                nome = rs.getString("nome"),
                cargo = rs.getString("cargo"),
                area = rs.getString("area"),
                senioridade = rs.getString("senioridade"),
                disponibilidade = rs.getInt("disponibilidade"),
                dataIngresso = rs.getDate("data_ingresso").toLocalDate(),
                localizacao = rs.getString("localizacao"),
                tecnologias = emptyList()
            )
        }
    }

    override fun listarTodos(): List<Funcionario> =
        dataSource.connection.use { conn ->
            val funcionarios = conn.prepareStatement(
                """
                SELECT f.id, f.nome, f.cargo, a.nome AS area, s.nome AS senioridade,
                       f.disponibilidade, f.data_ingresso, f.localizacao
                FROM funcionario f
                JOIN area a ON a.id = f.area_id
                LEFT JOIN senioridade s ON s.id = f.senioridade_id
                ORDER BY f.nome ASC
                """.trimIndent()
            ).use { stmts ->
                stmts.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(MAPER_BASE.mapRow(rs, 0)!!)
                    }
                }
            }

            val tecnologiasPorFuncionario = conn.prepareStatement(
                """
                SELECT ft.funcionario_id, t.nome
                FROM funcionario_tecnologia ft
                JOIN tecnologia t ON t.id = ft.tecnologia_id
                ORDER BY ft.funcionario_id ASC, t.nome ASC
                """.trimIndent()
            ).use { stmts ->
                stmts.executeQuery().use { rs ->
                    val mapa = mutableMapOf<String, MutableList<String>>()
                    while (rs.next()) {
                        mapa.getOrPut(rs.getString("funcionario_id")) { mutableListOf() }
                            .add(rs.getString("nome"))
                    }
                    mapa
                }
            }

            funcionarios.map { it.copy(tecnologias = tecnologiasPorFuncionario[it.id].orEmpty()) }
        }
}
