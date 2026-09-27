package com.noctua.api.repositories

import com.noctua.api.models.Usuario
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import javax.sql.DataSource

/**
 * Implementacao JDBC nativa de [UsuarioRepository].
 *
 * Todo acesso usa `Connection` / `PreparedStatement` / `ResultSet` obtained do
 * HikariCP. Nao ha named parameters nem mapeamento por anottacao: o
 * `RowMapper` converte cada linha explicitamente.
 */
@Repository
class UsuarioJdbcRepository(
    private val dataSource: DataSource
) : UsuarioRepository {

    private companion object {
        const val COLUNAS = "id, nome, email, senha_hash, papel, criado_em"

        val MAPER: RowMapper<Usuario> = RowMapper { rs, _ ->
            Usuario(
                id = rs.getLong("id"),
                nome = rs.getString("nome"),
                email = rs.getString("email"),
                senhaHash = rs.getString("senha_hash"),
                papel = rs.getString("papel"),
                criadoEm = rs.getObject("criado_em", OffsetDateTime::class.java).toInstant()
            )
        }
    }

    override fun buscarPorEmail(email: String): Usuario? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $COLUNAS FROM usuario WHERE email = ?"
            ).use { stmts ->
                stmts.setString(1, email)
                stmts.executeQuery().use { rs ->
                    if (rs.next()) MAPER.mapRow(rs, 0) else null
                }
            }
        }

    override fun buscarPorId(id: Long): Usuario? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $COLUNAS FROM usuario WHERE id = ?"
            ).use { stmts ->
                stmts.setLong(1, id)
                stmts.executeQuery().use { rs ->
                    if (rs.next()) MAPER.mapRow(rs, 0) else null
                }
            }
        }

    override fun existePorEmail(email: String): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM usuario WHERE email = ?"
            ).use { stmts ->
                stmts.setString(1, email)
                stmts.executeQuery().use { rs -> rs.next() }
            }
        }

    override fun criar(nome: String, email: String, senhaHash: String, papel: String): Usuario =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO usuario (nome, email, senha_hash, papel)
                VALUES (?, ?, ?, ?)
                RETURNING $COLUNAS
                """.trimIndent()
            ).use { stmts ->
                stmts.setString(1, nome)
                stmts.setString(2, email)
                stmts.setString(3, senhaHash)
                stmts.setString(4, papel)
                stmts.executeQuery().use { rs: ResultSet ->
                    rs.next()
                    MAPER.mapRow(rs, 0)!!
                }
            }
        }
}
