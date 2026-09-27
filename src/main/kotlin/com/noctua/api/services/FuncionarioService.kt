package com.noctua.api.services

import com.noctua.api.dto.FuncionarioResponse
import com.noctua.api.repositories.FuncionarioRepository
import org.springframework.stereotype.Service

/**
 * Quadro de equipe.
 *
 * Alimenta a aba Equipe e o assistente de criacao de projeto, que precisam do
 * catalogo de profissionais com cargo, area, senioridade e disponibilidade.
 */
@Service
class FuncionarioService(
    private val funcionarioRepository: FuncionarioRepository
) {

    fun listar(): List<FuncionarioResponse> =
        funcionarioRepository.listarTodos().map { funcionario ->
            FuncionarioResponse(
                id = funcionario.id,
                nome = funcionario.nome,
                cargo = funcionario.cargo,
                departamento = funcionario.area,
                senioridade = funcionario.senioridade,
                // O tipo `Employee` do frontend declara uma unica tecnologia
                // opcional; a API expoe a lista completa.
                tecnologia = funcionario.tecnologias.firstOrNull(),
                disponibilidade = funcionario.disponibilidade,
                dataIngresso = funcionario.dataIngresso,
                localizacao = funcionario.localizacao
            )
        }
}
