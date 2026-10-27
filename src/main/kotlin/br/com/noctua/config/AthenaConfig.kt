package br.com.noctua.config

import br.com.noctua.repository.AthenaQueryExecutor
import br.com.noctua.repository.AthenaUnavailableException
import br.com.noctua.repository.SdkAthenaQueryExecutor
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaClient
import java.time.Duration

@Validated
@ConfigurationProperties("noctua.athena")
data class AthenaProperties(
    val enabled: Boolean = false,
    val region: String = "us-east-1",
    val database: String = "",
    val catalog: String = "AwsDataCatalog",
    val workgroup: String = "primary",
    val outputLocation: String = "",
    val queryTimeout: Duration = Duration.ofSeconds(30),
    val pollInterval: Duration = Duration.ofMillis(500),
    @field:Min(1) val maxRows: Int = 10000,
    val queries: Map<String, String> = emptyMap(),
) {
    @get:AssertTrue(message = "Athena exige região, database, catalog, workgroup e quatro arquivos SQL configurados")
    val isConfigurationValid: Boolean
        get() = !enabled || (
            listOf(region, database, catalog, workgroup).all { it.isNotBlank() } &&
                listOf("total", "tecnologia", "senioridade", "area").all { !queries[it].isNullOrBlank() } &&
                (outputLocation.isBlank() || outputLocation.startsWith("s3://"))
            )

    @get:AssertTrue(message = "Os tempos do Athena devem ser positivos e polling menor ou igual ao timeout")
    val isTimingValid: Boolean
        get() = queryTimeout.toMillis() > 0 && pollInterval.toMillis() > 0 && pollInterval <= queryTimeout
}

@Configuration(proxyBeanMethods = false)
class AthenaConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "noctua.athena", name = ["enabled"], havingValue = "true")
    fun athenaClient(properties: AthenaProperties): AthenaClient = AthenaClient.builder()
        .region(Region.of(properties.region))
        // DefaultCredentialsProvider: variáveis AWS, perfil local ou IAM role da infraestrutura.
        .httpClientBuilder(UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(3))
            .socketTimeout(Duration.ofSeconds(5)))
        .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(5)) }
        .build()

    @Bean
    @ConditionalOnProperty(prefix = "noctua.athena", name = ["enabled"], havingValue = "true")
    fun athenaQueryExecutor(client: AthenaClient, properties: AthenaProperties): AthenaQueryExecutor =
        SdkAthenaQueryExecutor(client, properties)

    @Bean
    @ConditionalOnProperty(prefix = "noctua.athena", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun disabledAthenaQueryExecutor(): AthenaQueryExecutor = AthenaQueryExecutor {
        throw AthenaUnavailableException("Integração Athena não configurada")
    }
}
