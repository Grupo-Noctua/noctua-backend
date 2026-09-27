package com.noctua.api.repositories

import com.noctua.api.models.Alocacao
import com.noctua.api.models.MembroEquipe
import com.noctua.api.models.Projeto
import com.noctua.api.models.TecnologiaProjeto
import javax.sql.DataSource

interface ProjetoRepository {

    /** Todos os projetos ordenados por id, sem equipe. */
    fun listarTodos(): List<Projeto>

    fun buscarPorId(id: String): Projeto?

    /** IDs de todos os membros de todos os projetos, agrupaveis por [Alocacao.projetoId]. */
    fun listarAlocacoes(): List<Alocacao>

    /** Equipe completa (id, nome, cargo, area) de um projeto. */
    fun listarEquipe(projetoId: String): List<MembroEquipe>

    /** Tecnologias utilizadas no projeto, ordenadas pelo nivel de adocao decrescente. */
    fun listarTecnologias(projetoId: String): List<TecnologiaProjeto>
}
