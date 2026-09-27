package com.noctua.api.controllers

import com.noctua.api.dto.FuncionarioResponse
import com.noctua.api.services.FuncionarioService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/funcionarios")
class FuncionarioController(
    private val funcionarioService: FuncionarioService
) {

    @GetMapping
    fun listar(): List<FuncionarioResponse> = funcionarioService.listar()
}
