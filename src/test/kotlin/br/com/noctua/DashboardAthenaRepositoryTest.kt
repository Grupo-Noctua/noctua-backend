package br.com.noctua

import br.com.noctua.config.AthenaProperties
import br.com.noctua.repository.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader

class DashboardAthenaRepositoryTest {
    private fun repository(rows: List<Map<String, String?>>) = DashboardAthenaRepository(
        AthenaQueryExecutor { rows },
        AthenaProperties(enabled = true, queries = listOf("total", "tecnologia", "senioridade", "area")
            .associateWith { "classpath:athena/$it.sql" }),
        DefaultResourceLoader(),
    )

    @Test
    fun `total aceita Long acima do limite de Int`() {
        assertThat(repository(listOf(mapOf("total" to "3000000000"))).totalFuncionarios().total)
            .isEqualTo(3000000000L)
    }

    @Test
    fun `contagens invalidas nao viram zero`() {
        listOf(null, "", "abc", "1.5", "-1", "9223372036854775808").forEach {
            assertThatThrownBy { repository(listOf(mapOf("total" to it))).totalFuncionarios() }
                .isInstanceOf(AthenaUnavailableException::class.java)
        }
        assertThatThrownBy { repository(emptyList()).totalFuncionarios() }
            .isInstanceOf(AthenaUnavailableException::class.java)
        assertThatThrownBy { repository(listOf(mapOf("total" to "1"), mapOf("total" to "2"))).totalFuncionarios() }
            .isInstanceOf(AthenaUnavailableException::class.java)
    }

    @Test
    fun `mapeia dimensoes nulas e listas vazias mas exige aliases corretos`() {
        assertThat(repository(listOf(mapOf("tecnologia" to null, "quantidade" to "2")))
            .funcionariosPorTecnologia().single().tecnologia).isNull()
        assertThat(repository(emptyList()).funcionariosPorArea()).isEmpty()
        assertThat(repository(listOf(mapOf("area" to "Dados", "quantidade" to "4")))
            .funcionariosPorArea().single().area).isEqualTo("Dados")
        assertThat(repository(listOf(mapOf("senioridade" to "Senior", "quantidade" to "5")))
            .funcionariosPorSenioridade().single().quantidade).isEqualTo(5L)
        assertThatThrownBy { repository(listOf(mapOf("nome" to "Kotlin", "quantidade" to "2")))
            .funcionariosPorTecnologia() }.isInstanceOf(AthenaUnavailableException::class.java)
    }

    @Test
    fun `carrega SQL validado pelo recurso configurado`() {
        var captured = ""
        val repository = DashboardAthenaRepository(
            AthenaQueryExecutor { sql -> captured = sql; listOf(mapOf("total" to "42")) },
            AthenaProperties(enabled = true, queries = listOf("total", "tecnologia", "senioridade", "area")
                .associateWith { "classpath:athena/$it.sql" }),
            DefaultResourceLoader(),
        )
        assertThat(repository.totalFuncionarios().total).isEqualTo(42L)
        assertThat(captured.trim()).isEqualTo("SELECT 42 AS total")
    }
}
