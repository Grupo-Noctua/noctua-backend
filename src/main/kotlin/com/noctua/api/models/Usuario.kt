package com.noctua.api.models

import java.time.Instant

data class Usuario(
    val id: Long,
    val nome: String,
    val email: String,
    val senhaHash: String,
    val papel: String,
    val criadoEm: Instant
)
