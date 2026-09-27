package com.noctua.api.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "noctua.db")
data class DatabaseProperties(
    @Positive
    val maxPoolSize: Int = 10,
    @Positive
    val minimumIdle: Int = 2,
    val connectionTimeoutMs: Long = 10_000,
    val idleTimeoutMs: Long = 300_000,
    val maxLifetimeMs: Long = 1_200_000
)

@Validated
@ConfigurationProperties(prefix = "noctua.jwt")
data class JwtProperties(
    @field:NotBlank
    val secret: String,
    @field:Positive
    val expirationMinutes: Long = 720,
    val issuer: String = "noctua-api"
)

@Validated
@ConfigurationProperties(prefix = "noctua.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf("http://localhost:5173")
)
