package com.noctua.api.services

import com.noctua.api.dto.DocumentoOrigemResponse
import com.noctua.api.dto.MembroEquipeResponse
import com.noctua.api.dto.ProjetoDetalheResponse
import com.noctua.api.dto.ProjetoResumoResponse
import com.noctua.api.dto.RecursoNaoEncontradoException
import com.noctua.api.models.Projeto
import com.noctua.api.repositories.ProjetoRepository
import org.springframework.stereotype.Service

@Service
class ProjetoService(
    private val projetoRepository: ProjetoRepository
) {

    /**
     * Tabela de projetos.
     *
     * `equipe` sai como lista de IDs, que e o que o frontend usa para
     * desenhar o stack de avatares. Todos os IDs sao buscados em uma unica
     * consulta e agrupados por projeto — nao ha consulta por linha.
     */
    fun listar(): List<ProjetoResumoResponse> {
        val projetos = projetoRepository.listarTodos()
        if (projetos.isEmpty()) return emptyList()

        val idsPorProjeto = projetoRepository.listarAlocacoes()
            .groupBy({ it.projetoId }, { it.funcionarioId })

        return projetos.map { projeto ->
            projeto.paraResumo(equipe = idsPorProjeto[projeto.id].orEmpty())
        }
    }

    /**
     * Detalhamento acionado ao clicar em um item da tabela.
     *
     * Traz nome completo, cargo e area de cada alocado, alem das tecnologias
     * utilizadas.
     */
    fun buscarPorId(id: String): ProjetoDetalheResponse {
        val projeto = projetoRepository.buscarPorId(id)
            ?: throw RecursoNaoEncontradoException("Projeto $id nao encontrado")

        val equipe = projetoRepository.listarEquipe(id).map { membro ->
            MembroEquipeResponse(
                id = membro.id,
                nome = membro.nome,
                cargo = membro.cargo,
                departamento = membro.area,
                senioridade = membro.senioridade
            )
        }

        val tecnologias = projetoRepository.listarTecnologias(id).map { it.nome }

        return ProjetoDetalheResponse(
            id = projeto.id,
            nome = projeto.nome,
            cliente = projeto.cliente,
            status = projeto.status.rotulo,
            progresso = projeto.progresso,
            orcamento = projeto.orcamento,
            gasto = projeto.gasto,
            deadline = projeto.deadline,
            fase = projeto.fase,
            documentoOrigem = DocumentoOrigemResponse(
                bucket = projeto.documentoOrigem.bucket,
                objectKey = projeto.documentoOrigem.objectKey,
                lastModified = projeto.documentoOrigem.lastModified
            ),
            equipe = equipe,
            tecnologias = tecnologias
        )
    }

    private fun Projeto.paraResumo(equipe: List<String>) = ProjetoResumoResponse(
        id = id,
        nome = nome,
        cliente = cliente,
        status = status.rotulo,
        progresso = progresso,
        orcamento = orcamento,
        gasto = gasto,
        deadline = deadline,
        fase = fase,
        equipe = equipe
    )
}
