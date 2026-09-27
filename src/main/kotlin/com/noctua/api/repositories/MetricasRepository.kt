package com.noctua.api.repositories

import com.noctua.api.models.AgregadoRotuloQuantidade

interface MetricasRepository {

    fun contarFuncionarios(): Int

    fun contarProjetos(): Int

    /** Agregado bruto de usos por tecnologia; o percentual e calculado no servico. */
    fun usosPorTecnologia(): List<AgregadoRotuloQuantidade>

    /**
     * Tecnologia com maior somatorio de nivel de adocao. Empates sao
     * resolvidos pelo nome em ordem alfabetica para manter o resultado
     * deterministico.
     */
    fun tecnologiaMaisUtilizada(): String?

    /**
     * Contagem de vinculos funcionario x tecnologia.
     *
     * Retorna o numero de LINHAS (nao de funcionarios distintos): com 80
     * funcionarios e um profissional em duas tecnologias o resultado e 81.
     */
    fun vinculosPorTecnologia(): List<AgregadoRotuloQuantidade>

    /** Contagem por area funcional, ja ordenada de forma decrescente. */
    fun funcionariosPorArea(): List<AgregadoRotuloQuantidade>

    /**
     * Contagem por nivel de senioridade na ordem da hierarquia, com
     * preenchimento zero: a resposta sempre tem 6 linhas.
     */
    fun funcionariosPorSenioridade(): List<AgregadoRotuloQuantidade>
}
