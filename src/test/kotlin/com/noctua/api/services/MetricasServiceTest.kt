package com.noctua.api.services

import com.noctua.api.models.AgregadoRotuloQuantidade
import com.noctua.api.repositories.MetricasRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@ExtendWith(MockKExtension::class)
@DisplayName("MetricasService - agregados do dashboard")
class MetricasServiceTest {

    @MockK
    private lateinit var repository: MetricasRepository

    private lateinit var service: MetricasService

    @BeforeEach
    fun setUp() {
        service = MetricasService(repository)
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/kpis")
    inner class Kpis {

        @Test
        fun `retorna os tres cards com os valores da massa de dados`() {
            every { repository.contarFuncionarios() } returns 80
            every { repository.contarProjetos() } returns 6
            every { repository.tecnologiaMaisUtilizada() } returns "Node.js"

            val kpis = service.kpis()

            assertThat(kpis.totalFuncionarios).isEqualTo(80)
            assertThat(kpis.totalProjetosAnalisados).isEqualTo(6)
            assertThat(kpis.ferramentaMaisUtilizada).isEqualTo("Node.js")
        }

        @Test
        fun `usa travessao quando nao ha tecnologia cadastrada`() {
            every { repository.contarFuncionarios() } returns 0
            every { repository.contarProjetos() } returns 0
            every { repository.tecnologiaMaisUtilizada() } returns null

            assertThat(service.kpis().ferramentaMaisUtilizada).isEqualTo("—")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/funcionarios-tecnologia")
    inner class FuncionariosTecnologia {

        @Test
        fun `mantem os 9 vinculos na ordem do grafico`() {
            every { repository.vinculosPorTecnologia() } returns agregar(
                "Node.js" to 11, "Spark" to 8, "React" to 10, "Postgres" to 7, "Docker" to 13,
                "Figma" to 6, "AWS" to 9, "PowerBI" to 5, "Python" to 12
            )

            val resultado = service.funcionariosPorTecnologia()

            assertThat(resultado).hasSize(9)
            assertThat(resultado.map { it.tecnologia })
                .containsExactly("Node.js", "Spark", "React", "Postgres", "Docker", "Figma", "AWS", "PowerBI", "Python")
            assertThat(resultado.map { it.quantidade })
                .containsExactly(11, 8, 10, 7, 13, 6, 9, 5, 12)
        }

        @Test
        fun `soma 81 para 80 funcionarios porque conta vinculos e nao pessoas`() {
            every { repository.vinculosPorTecnologia() } returns agregar(
                "Node.js" to 11, "Spark" to 8, "React" to 10, "Postgres" to 7, "Docker" to 13,
                "Figma" to 6, "AWS" to 9, "PowerBI" to 5, "Python" to 12
            )

            // Documenta a diferenca deliberada em relacao ao KPI de 80.
            assertThat(service.funcionariosPorTecnologia().sumOf { it.quantidade }).isEqualTo(81)
        }

        @Test
        fun `devolve lista vazia quando nao ha vinculos`() {
            every { repository.vinculosPorTecnologia() } returns emptyList()

            assertThat(service.funcionariosPorTecnologia()).isEmpty()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/funcionarios-area")
    inner class FuncionariosArea {

        @Test
        fun `devolve 16 por area preservando a ordem decrescente do repositorio`() {
            every { repository.funcionariosPorArea() } returns agregar(
                "TI" to 16, "Dados" to 16, "Design" to 16, "Infra" to 16, "Consultoria" to 16
            )

            val resultado = service.funcionariosPorArea()

            assertThat(resultado).hasSize(5)
            assertThat(resultado.sumOf { it.quantidade }).isEqualTo(80)
            assertThat(resultado.first().area).isEqualTo("TI")
        }

        @Test
        fun `preserva a ordem de contagem decrescente devolvida pelo banco`() {
            every { repository.funcionariosPorArea() } returns agregar(
                "Dados" to 30, "TI" to 20, "Design" to 10
            )

            assertThat(service.funcionariosPorArea().map { it.area })
                .containsExactly("Dados", "TI", "Design")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/funcionarios-senioridade")
    inner class FuncionariosSenioridade {

        @Test
        fun `devolve os 6 niveis na hierarquia com 14,14,13,13,13,13`() {
            every { repository.funcionariosPorSenioridade() } returns agregar(
                "Estagiário" to 14, "Júnior" to 14, "Pleno" to 13,
                "Sênior" to 13, "Especialista" to 13, "Tech Lead" to 13
            )

            val resultado = service.funcionariosPorSenioridade()

            assertThat(resultado.map { it.senioridade }).containsExactly(
                "Estagiário", "Júnior", "Pleno", "Sênior", "Especialista", "Tech Lead"
            )
            assertThat(resultado.sumOf { it.quantidade }).isEqualTo(80)
        }

        @Test
        fun `mantem nivel sem ninguem com quantidade zero em vez de omitir`() {
            every { repository.funcionariosPorSenioridade() } returns agregar(
                "Estagiário" to 0, "Júnior" to 40, "Pleno" to 40,
                "Sênior" to 0, "Especialista" to 0, "Tech Lead" to 0
            )

            val resultado = service.funcionariosPorSenioridade()

            assertThat(resultado).hasSize(6)
            assertThat(resultado.first().quantidade).isZero()
            assertThat(resultado[1].quantidade).isEqualTo(40)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/projetos-tecnologia")
    inner class ProjetosTecnologia {

        @Test
        fun `calcula o percentual sobre o total de usos de 79`() {
            every { repository.usosPorTecnologia() } returns agregar(
                "Node.js" to 11, "Spark" to 11, "React" to 9, "Postgres" to 9, "Docker" to 9,
                "Figma" to 9, "AWS" to 7, "PowerBI" to 7, "Python" to 7
            )

            val resultado = service.projetosPorTecnologia()

            assertThat(resultado).hasSize(9)
            assertThat(resultado.sumOf { it.quantidade }).isEqualTo(79)
            assertThat(resultado.map { it.percentual })
                .containsExactly(14, 14, 11, 11, 11, 11, 9, 9, 9)
        }

        @Test
        fun `percentuais de um unico item somam 100`() {
            every { repository.usosPorTecnologia() } returns agregar("Node.js" to 79)

            val resultado = service.projetosPorTecnologia()

            assertThat(resultado.single().percentual).isEqualTo(100)
        }

        @ParameterizedTest(name = "{0} de {1} usos resulta em {2}%")
        @CsvSource(
            "11, 79, 14",
            "9,  79, 11",
            "7,  79, 9",
            "50, 200, 25",
            "1,  3,  33"
        )
        fun `arredonda o percentual para o inteiro mais proximo`(parte: Int, total: Int, esperado: Int) {
            every { repository.usosPorTecnologia() } returns listOf(
                AgregadoRotuloQuantidade("Alvo", parte),
                AgregadoRotuloQuantidade("Resto", total - parte)
            )

            val alvo = service.projetosPorTecnologia().first { it.tecnologia == "Alvo" }

            assertThat(alvo.percentual).isEqualTo(esperado)
        }

        @Test
        fun `devolve lista vazia quando o total de usos e zero, evitando divisao por zero`() {
            every { repository.usosPorTecnologia() } returns emptyList()

            assertThat(service.projetosPorTecnologia()).isEmpty()
        }
    }

    private fun agregar(vararg pares: Pair<String, Int>): List<AgregadoRotuloQuantidade> =
        pares.map { (rotulo, quantidade) -> AgregadoRotuloQuantidade(rotulo, quantidade) }
}
