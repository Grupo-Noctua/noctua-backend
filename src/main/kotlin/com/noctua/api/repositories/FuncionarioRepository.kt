package com.noctua.api.repositories

import com.noctua.api.models.Funcionario

interface FuncionarioRepository {
    /**
     * Lista o quadro completo com as tecnologias de cada profissional.
     *
     * Usa tres consultas e costura o resultado em memoria, evitando o
     * padrao N+1 (uma consulta por funcionario).
     */
    fun listarTodos(): List<Funcionario>
}
