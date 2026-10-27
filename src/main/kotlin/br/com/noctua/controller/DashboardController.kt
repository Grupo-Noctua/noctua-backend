package br.com.noctua.controller

import br.com.noctua.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val service: DashboardService) {
    @GetMapping("/funcionarios/total")
    fun totalFuncionarios() = service.totalFuncionarios()

    @GetMapping("/projetos/total")
    fun totalProjetos() = service.totalProjetos()

    @GetMapping("/ferramentas/mais-utilizada")
    fun ferramentasMaisUtilizadas() = service.ferramentasMaisUtilizadas()

    @GetMapping("/funcionarios/tecnologia")
    fun funcionariosPorTecnologia() = service.funcionariosPorTecnologia()

    @GetMapping("/funcionarios/senioridade")
    fun funcionariosPorSenioridade() = service.funcionariosPorSenioridade()

    @GetMapping("/funcionarios/area")
    fun funcionariosPorArea() = service.funcionariosPorArea()

    @GetMapping("/projetos/tecnologia")
    fun projetosPorTecnologia() = service.projetosPorTecnologia()

    @GetMapping("/projetos")
    fun projetos() = service.projetos()
}
