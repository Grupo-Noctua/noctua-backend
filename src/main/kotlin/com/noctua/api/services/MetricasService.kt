package com.noctua.api.services

import com.noctua.api.dto.AreaFuncionarioResponse
import com.noctua.api.dto.KpiResponse
import com.noctua.api.dto.SenioridadeFuncionarioResponse
import com.noctua.api.dto.TecnologiaFuncionarioResponse
import com.noctua.api.dto.TecnologiaProjetoResponse
import com.noctua.api.models.AgregadoRotuloQuantidade
import com.noctua.api.repositories.MetricasRepository
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * Calculo dos agregados do dashboard de Metricas.
 *
 * O banco entrega contagens ja agrupadas; este servico acrescenta apenas a
 * regra de negocio que depende do conjunto inteiro — a participacao
 * percentual de cada tecnologia na rosca.
 */
@Service
class MetricasService(
    private val metricasRepository: MetricasRepository
) {

    private companion object {
        /** Valor exibido quando nao ha nenhuma tecnologia cadastrada. */
        const val SEM_DADOS = "—"
    }

    fun kpis(): KpiResponse = KpiResponse(
        totalFuncionarios = metricasRepository.contarFuncionarios(),
        totalProjetosAnalisados = metricasRepository.contarProjetos(),
        ferramentaMaisUtilizada = metricasRepository.tecnologiaMaisUtilizada() ?: SEM_DADOS
    )

    /**
     * Grafico de colunas "Funcionarios x Tecnologia".
     *
     * A contagem e de VINCULOS funcionario-tecnologia, e nao de pessoas. Com
     * 80 funcionarios e um deles em duas tecnologias a soma e 81; por isso
     * este total nao deve ser comparado com `total_funcionarios`.
     */
    fun funcionariosPorTecnologia(): List<TecnologiaFuncionarioResponse> =
        metricasRepository.vinculosPorTecnologia().map { it.paraTecnologiaFuncionario() }

    /** Barras horizontais por area funcional, ja ordenadas de forma decrescente. */
    fun funcionariosPorArea(): List<AreaFuncionarioResponse> =
        metricasRepository.funcionariosPorArea().map { (rotulo, quantidade) ->
            AreaFuncionarioResponse(area = rotulo, quantidade = quantidade)
        }

    /**
     * Barras horizontais por senioridade.
     *
     * A hierarquia de 6 niveis e fixa: um nivel sem nenhuma pessoa aparece
     * com quantidade zero, em vez de sumir do grafico.
     */
    fun funcionariosPorSenioridade(): List<SenioridadeFuncionarioResponse> =
        metricasRepository.funcionariosPorSenioridade()
            .map { (rotulo, quantidade) ->
                SenioridadeFuncionarioResponse(senioridade = rotulo, quantidade = quantidade)
            }

    /**
     * Rosca "Projetos x Tecnologia".
     *
     * O percentual e a participacao de cada tecnologia no total de usos, e
     * nao no total de projetos: a legenda do grafico diz "X proj.", mas com
     * 6 projetos cadastrados a contagem jamais passaria de 6, enquanto o
     * desenho espera valores entre 7 e 11. O valor exibido e o somatorio dos
     * niveis de adocao (1..3) da tecnologia nos projetos.
     */
    fun projetosPorTecnologia(): List<TecnologiaProjetoResponse> {
        val usos = metricasRepository.usosPorTecnologia()
        val totalGeral = usos.sumOf { it.quantidade }

        if (totalGeral == 0) {
            return emptyList()
        }

        return usos.map { (rotulo, quantidade) ->
            TecnologiaProjetoResponse(
                tecnologia = rotulo,
                quantidade = quantidade,
                percentual = percentual(quantidade, totalGeral)
            )
        }
    }

    private fun percentual(parte: Int, total: Int): Int =
        ((parte.toDouble() / total.toDouble()) * 100.0).roundToInt()

    private fun AgregadoRotuloQuantidade.paraTecnologiaFuncionario() =
        TecnologiaFuncionarioResponse(tecnologia = rotulo, quantidade = quantidade)
}
