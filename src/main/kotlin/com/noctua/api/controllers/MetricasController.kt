package com.noctua.api.controllers

import com.noctua.api.dto.AreaFuncionarioResponse
import com.noctua.api.dto.KpiResponse
import com.noctua.api.dto.SenioridadeFuncionarioResponse
import com.noctua.api.dto.TecnologiaFuncionarioResponse
import com.noctua.api.dto.TecnologiaProjetoResponse
import com.noctua.api.services.MetricasService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Dashboard de Metricas.
 *
 * Todas as rotas exigem token valido (ver `SecurityConfig`). Os nomes das
 * properties JSON seguem o contrato do frontend: `tecnologia`, `area`,
 * `senioridade` e `quantidade`.
 */
@RestController
@RequestMapping("/api/v1/metrics")
class MetricasController(
    private val metricasService: MetricasService
) {

    @GetMapping("/kpis")
    fun kpis(): KpiResponse = metricasService.kpis()

    @GetMapping("/funcionarios-tecnologia")
    fun funcionariosPorTecnologia(): List<TecnologiaFuncionarioResponse> =
        metricasService.funcionariosPorTecnologia()

    @GetMapping("/funcionarios-area")
    fun funcionariosPorArea(): List<AreaFuncionarioResponse> =
        metricasService.funcionariosPorArea()

    @GetMapping("/funcionarios-senioridade")
    fun funcionariosPorSenioridade(): List<SenioridadeFuncionarioResponse> =
        metricasService.funcionariosPorSenioridade()

    @GetMapping("/projetos-tecnologia")
    fun projetosPorTecnologia(): List<TecnologiaProjetoResponse> =
        metricasService.projetosPorTecnologia()
}
