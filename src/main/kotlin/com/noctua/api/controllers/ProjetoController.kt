package com.noctua.api.controllers

import com.noctua.api.dto.ProjetoDetalheResponse
import com.noctua.api.dto.ProjetoResumoResponse
import com.noctua.api.services.ProjetoService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/projetos")
class ProjetoController(
    private val projetoService: ProjetoService
) {

    /**
     * Tabela de projetos, com `equipe` como lista de IDs de funcionarios.
     * A busca por nome permanece no cliente, como esta no frontend.
     */
    @GetMapping
    fun listar(): List<ProjetoResumoResponse> = projetoService.listar()

    /** Detalhamento: equipe completa (nome, cargo, area) e tecnologias do projeto. */
    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: String): ProjetoDetalheResponse =
        projetoService.buscarPorId(id)
}
