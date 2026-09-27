package com.noctua.api.services

import com.noctua.api.dto.RecursoNaoEncontradoException
import com.noctua.api.models.Alocacao
import com.noctua.api.models.DocumentoOrigem
import com.noctua.api.models.MembroEquipe
import com.noctua.api.models.Projeto
import com.noctua.api.models.StatusProjeto
import com.noctua.api.models.TecnologiaProjeto
import com.noctua.api.repositories.ProjetoRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@ExtendWith(MockKExtension::class)
@DisplayName("ProjetoService - listagem e detalhamento")
class ProjetoServiceTest {

    @MockK
    private lateinit var repository: ProjetoRepository

    private lateinit var service: ProjetoService

    private val portalCliente = projeto(
        id = "p1",
        nome = "Portal Cliente",
        cliente = "Empresa A",
        status = StatusProjeto.EM_DIA,
        progresso = 78,
        orcamento = BigDecimal("800000.00"),
        gasto = BigDecimal("400000.00"),
        deadline = LocalDate.of(2026, 11, 30),
        fase = "Desenvolvimento"
    )

    private val erpModernizacao = projeto(
        id = "p2",
        nome = "ERP Modernização",
        cliente = "Empresa B",
        status = StatusProjeto.EM_RISCO,
        progresso = 45,
        orcamento = BigDecimal("1200000.00"),
        gasto = BigDecimal("900000.00"),
        deadline = LocalDate.of(2026, 9, 10),
        fase = "Implementação"
    )

    @BeforeEach
    fun setUp() {
        service = ProjetoService(repository)
    }

    @Nested
    @DisplayName("GET /api/v1/projetos")
    inner class Listar {

        @Test
        fun `mapeia todos os campos da linha da tabela`() {
            every { repository.listarTodos() } returns listOf(portalCliente)
            every { repository.listarAlocacoes() } returns listOf(
                Alocacao("p1", "u1"), Alocacao("p1", "u2"), Alocacao("p1", "u3")
            )

            val projeto = service.listar().single()

            assertThat(projeto.id).isEqualTo("p1")
            assertThat(projeto.nome).isEqualTo("Portal Cliente")
            assertThat(projeto.cliente).isEqualTo("Empresa A")
            assertThat(projeto.fase).isEqualTo("Desenvolvimento")
            assertThat(projeto.status).isEqualTo("Em dia")
            assertThat(projeto.progresso).isEqualTo(78)
            assertThat(projeto.orcamento).isEqualByComparingTo("800000.00")
            assertThat(projeto.gasto).isEqualByComparingTo("400000.00")
            assertThat(projeto.deadline).isEqualTo("2026-11-30")
        }

        @Test
        fun `expoe a equipe como lista de IDs na ordem devolvida pelo banco`() {
            every { repository.listarTodos() } returns listOf(portalCliente)
            every { repository.listarAlocacoes() } returns listOf(
                Alocacao("p1", "u1"), Alocacao("p1", "u2"), Alocacao("p1", "u3")
            )

            assertThat(service.listar().single().equipe).containsExactly("u1", "u2", "u3")
        }

        @Test
        fun `agrupa a equipe pelo projeto, sem misturar os membros entre linhas`() {
            every { repository.listarTodos() } returns listOf(portalCliente, erpModernizacao)
            every { repository.listarAlocacoes() } returns listOf(
                Alocacao("p1", "u1"), Alocacao("p1", "u2"), Alocacao("p1", "u3"),
                Alocacao("p2", "u4"), Alocacao("p2", "u5")
            )

            val lista = service.listar()

            assertThat(lista).hasSize(2)
            assertThat(lista[0].equipe).containsExactly("u1", "u2", "u3")
            assertThat(lista[1].equipe).containsExactly("u4", "u5")
        }

        @Test
        fun `mantem o status como rotulo acentuado, e nao como nome do enum`() {
            every { repository.listarTodos() } returns listOf(erpModernizacao)
            every { repository.listarAlocacoes() } returns emptyList()

            assertThat(service.listar().single().status).isEqualTo("Em risco")
        }

        @Test
        fun `devolve equipe vazia para projeto sem alocacoes`() {
            every { repository.listarTodos() } returns listOf(portalCliente)
            every { repository.listarAlocacoes() } returns emptyList()

            assertThat(service.listar().single().equipe).isEmpty()
        }

        @Test
        fun `devolve lista vazia sem consultar alocacoes quando nao ha projetos`() {
            every { repository.listarTodos() } returns emptyList()

            assertThat(service.listar()).isEmpty()
            verify(exactly = 0) { repository.listarAlocacoes() }
        }

        @Test
        fun `nao expoe os metadados S3 na listagem`() {
            every { repository.listarTodos() } returns listOf(portalCliente)
            every { repository.listarAlocacoes() } returns emptyList()

            // A listagem alimenta a tabela; os metadados do bucket ficam
            // restritos ao detalhamento.
            val resposta = service.listar().single()
            assertThat(resposta.javaClass.declaredFields.map { it.name })
                .doesNotContain("documentoOrigem")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projetos/{id}")
    inner class BuscarPorId {

        @Test
        fun `traz nome completo, cargo e area de cada membro`() {
            every { repository.buscarPorId("p1") } returns portalCliente
            every { repository.listarEquipe("p1") } returns listOf(
                MembroEquipe("u1", "Ana Silva", "Analista", "TI", "Estagiário"),
                MembroEquipe("u2", "Bruno Costa", "Desenvolvedor", "Dados", "Júnior")
            )
            every { repository.listarTecnologias("p1") } returns emptyList()

            val equipe = service.buscarPorId("p1").equipe

            assertThat(equipe).hasSize(2)
            assertThat(equipe[0].nome).isEqualTo("Ana Silva")
            assertThat(equipe[0].cargo).isEqualTo("Analista")
            assertThat(equipe[0].departamento).isEqualTo("TI")
            assertThat(equipe[0].senioridade).isEqualTo("Estagiário")
            assertThat(equipe[1].departamento).isEqualTo("Dados")
        }

        @Test
        fun `devolve o senioridade nulo quando o funcionario nao tem nivel definido`() {
            every { repository.buscarPorId("p1") } returns portalCliente
            every { repository.listarEquipe("p1") } returns listOf(
                MembroEquipe("u9", "Igor Santos", "Designer", "Design", null)
            )
            every { repository.listarTecnologias("p1") } returns emptyList()

            assertThat(service.buscarPorId("p1").equipe.single().senioridade).isNull()
        }

        @Test
        fun `lista as tecnologias utilizadas no projeto`() {
            every { repository.buscarPorId("p1") } returns portalCliente
            every { repository.listarEquipe("p1") } returns emptyList()
            every { repository.listarTecnologias("p1") } returns listOf(
                TecnologiaProjeto("Node.js", 3),
                TecnologiaProjeto("React", 2),
                TecnologiaProjeto("Postgres", 1)
            )

            assertThat(service.buscarPorId("p1").tecnologias)
                .containsExactly("Node.js", "React", "Postgres")
        }

        @Test
        fun `inclui os metadados do documento no bucket S3`() {
            every { repository.buscarPorId("p1") } returns portalCliente
            every { repository.listarEquipe("p1") } returns emptyList()
            every { repository.listarTecnologias("p1") } returns emptyList()

            val origem = service.buscarPorId("p1").documentoOrigem

            assertThat(origem.bucket).isEqualTo("noctua-projetos-documentos")
            assertThat(origem.objectKey).isEqualTo("2026/p1-documentacao.pdf")
            assertThat(origem.lastModified).isEqualTo(Instant.parse("2026-01-12T09:15:00Z"))
        }

        @Test
        fun `repassa os dados comercials do projeto`() {
            every { repository.buscarPorId("p1") } returns portalCliente
            every { repository.listarEquipe("p1") } returns emptyList()
            every { repository.listarTecnologias("p1") } returns emptyList()

            val detalhe = service.buscarPorId("p1")

            assertThat(detalhe.nome).isEqualTo("Portal Cliente")
            assertThat(detalhe.cliente).isEqualTo("Empresa A")
            assertThat(detalhe.fase).isEqualTo("Desenvolvimento")
            assertThat(detalhe.orcamento).isEqualByComparingTo("800000.00")
            assertThat(detalhe.deadline).isEqualTo("2026-11-30")
            assertThat(detalhe.status).isEqualTo("Em dia")
        }

        @Test
        fun `lança 404 quando o projeto nao existe`() {
            every { repository.buscarPorId("p99") } returns null

            assertThatThrownBy { service.buscarPorId("p99") }
                .isInstanceOf(RecursoNaoEncontradoException::class.java)
                .hasMessage("Projeto p99 nao encontrado")

            verify(exactly = 0) { repository.listarEquipe(any()) }
            verify(exactly = 0) { repository.listarTecnologias(any()) }
        }
    }

    private fun projeto(
        id: String,
        nome: String,
        cliente: String,
        status: StatusProjeto,
        progresso: Int,
        orcamento: BigDecimal,
        gasto: BigDecimal,
        deadline: LocalDate,
        fase: String
    ) = Projeto(
        id = id,
        nome = nome,
        cliente = cliente,
        status = status,
        progresso = progresso,
        orcamento = orcamento,
        gasto = gasto,
        deadline = deadline,
        fase = fase,
        documentoOrigem = DocumentoOrigem(
            bucket = "noctua-projetos-documentos",
            objectKey = "2026/${id}-documentacao.pdf",
            lastModified = Instant.parse("2026-01-12T09:15:00Z")
        )
    )
}
