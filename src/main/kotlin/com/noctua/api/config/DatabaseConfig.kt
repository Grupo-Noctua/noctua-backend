package com.noctua.api.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Pool de conexoes HikariCP sobre o driver JDBC nativo do PostgreSQL.
 *
 * O acesso aos dados e 100% JDBC cru (Connection / PreparedStatement /
 * ResultSet): nao ha JPA nem JdbcTemplate em nenhum ponto da aplicacao.
 * Este bean existe para deixar o dimensionamento do pool explicito e
 * configuravel por ambiente, em vez do auto-tuning do Spring Boot.
 *
 * `autoCommit` fica ligado de proposito. Nenhuma operacao desta aplicacao
 * faz escrita parcial, portanto nao ha necessidade de demarcacao transacional
 * — e sem um PlatformTransactionManager ligado, deixar autoCommit desligado
 * devolveria conexoes ao pool com transacao aberta.
 */
@Configuration
class DatabaseConfig {

    @Bean
    @Primary
    fun dataSource(
        dbProperties: DatabaseProperties,
        dataSourceProperties: DataSourceProperties
    ): DataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = dataSourceProperties.determineUrl()
            username = dataSourceProperties.determineUsername()
            password = dataSourceProperties.determinePassword()
            driverClassName = dataSourceProperties.determineDriverClassName()

            maximumPoolSize = dbProperties.maxPoolSize
            minimumIdle = dbProperties.minimumIdle
            connectionTimeout = dbProperties.connectionTimeoutMs
            idleTimeout = dbProperties.idleTimeoutMs
            maxLifetime = dbProperties.maxLifetimeMs

            poolName = "noctua-cp"
            isAutoCommit = true
            connectionInitSql = "SET TIME ZONE 'UTC'"
        }
        return HikariDataSource(hikari)
    }
}
