package com.noctua.api.controllers

import com.noctua.api.dto.LoginRequest
import com.noctua.api.dto.LoginResponse
import com.noctua.api.dto.RegisterRequest
import com.noctua.api.dto.UsuarioResponse
import com.noctua.api.security.JwtService
import com.noctua.api.services.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun registrar(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UsuarioResponse> {
        val usuario = authService.registrar(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse =
        authService.autenticar(request)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: JwtService.Principal): UsuarioResponse =
        authService.perfil(principal.id)
}
