package br.com.noctua

import br.com.noctua.repository.DashboardJdbcRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = ["/schema.sql", "/data.sql"])
class DashboardIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var repository: DashboardJdbcRepository

    @Test
    fun `conta processamentos inclusive os sem documento ou matching`() {
        mvc.get("/api/dashboard/projetos/total").andExpect {
            status { isOk() }; content { json("""{"total":4}""") }
        }
    }

    @Test
    fun `ferramentas retorna todos os empates sem contar competencia repetida na vaga`() {
        mvc.get("/api/dashboard/ferramentas/mais-utilizada").andExpect {
            status { isOk() }
            content { json("""[{"ferramenta":"Java","quantidade":2},{"ferramenta":"SQL","quantidade":2}]""") }
        }
    }

    @Test
    fun `tecnologia conta projetos distintos e nao vagas`() {
        mvc.get("/api/dashboard/projetos/tecnologia").andExpect {
            status { isOk() }
            content { json("""[{"tecnologia":"Java","quantidade":2},{"tecnologia":"AWS","quantidade":1},{"tecnologia":"SQL","quantidade":1}]""") }
        }
    }

    @Test
    fun `lista todos os projetos agrupa candidatos preserva repeticoes e ranking`() {
        mvc.get("/api/dashboard/projetos").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
            jsonPath("$[0].idProcessamento") { value(1) }
            jsonPath("$[0].idProjeto") { value(101) }
            jsonPath("$[0].dataInicioPrevista") { value("2026-10-15") }
            jsonPath("$[0].orcamento") { value(350000.00) }
            jsonPath("$[0].profissionaisRecomendados.length()") { value(3) }
            jsonPath("$[0].profissionaisRecomendados[0].idProfissional") { value("PROF-001") }
            jsonPath("$[0].profissionaisRecomendados[1].posicaoRanking") { value(2) }
            jsonPath("$[0].profissionaisRecomendados[2].idProfissional") { value("PROF-001") }
            jsonPath("$[0].profissionaisRecomendados[2].vaga") { value("Arquiteto de Dados") }
            jsonPath("$[1].orcamento") { value(null) }
            jsonPath("$[1].profissionaisRecomendados") { isEmpty() }
            jsonPath("$[2].profissionaisRecomendados") { isEmpty() }
            jsonPath("$[3].idProjeto") { value(null) }
            jsonPath("$[3].profissionaisRecomendados") { isEmpty() }
        }
        assertThat(repository.projetos().first { it.projeto.idProcessamento == 3L }.projeto.orcamento)
            .isEqualTo(BigDecimal("9999999999999.99"))
        assertThat(repository.projetos().first().profissional!!.scoreTotal).isEqualTo(BigDecimal("95.40"))
    }

    @Test
    @Sql(scripts = ["/schema.sql"])
    fun `base vazia retorna zero e listas vazias`() {
        mvc.get("/api/dashboard/projetos/total").andExpect {
            status { isOk() }; content { json("""{"total":0}""") }
        }
        listOf("projetos", "projetos/tecnologia", "ferramentas/mais-utilizada").forEach {
            mvc.get("/api/dashboard/$it").andExpect { status { isOk() }; content { json("[]") } }
        }
    }

    @Test
    fun `falha SQL retorna erro padronizado sem detalhes da infraestrutura`() {
        jdbc.execute("DROP TABLE Vaga_Competencia")
        mvc.get("/api/dashboard/ferramentas/mais-utilizada").andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.status") { value(500) }
            jsonPath("$.detail") { value("Não foi possível consultar os dados do dashboard.") }
            jsonPath("$.errorId") { isNotEmpty() }
            jsonPath("$.trace") { doesNotExist() }
        }
    }

    @Test
    fun `Athena desabilitado nao retorna indicadores ficticios`() {
        listOf("total", "tecnologia", "senioridade", "area").forEach {
            mvc.get("/api/dashboard/funcionarios/$it").andExpect {
                status { isInternalServerError() }; jsonPath("$.errorId") { isNotEmpty() }
            }
        }
    }

    @Test
    fun `rotas e metodos inexistentes mantem status HTTP apropriado`() {
        mvc.get("/api/dashboard/inexistente").andExpect { status { isNotFound() } }
        mvc.post("/api/dashboard/projetos").andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `documentos ambiguos nao sao descartados silenciosamente`() {
        jdbc.update("INSERT INTO Documento_Projeto (id_documento, fk_processamento) VALUES (104, 1)")
        mvc.get("/api/dashboard/projetos").andExpect { status { isInternalServerError() } }
    }
}
