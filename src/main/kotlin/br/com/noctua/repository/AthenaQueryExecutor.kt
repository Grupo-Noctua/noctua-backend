package br.com.noctua.repository

import br.com.noctua.config.AthenaProperties
import org.slf4j.LoggerFactory
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.*
import java.time.Duration

class AthenaUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun interface AthenaQueryExecutor {
    fun execute(sql: String): List<Map<String, String?>>
}

/** Orçamento de tempo compartilhado pelo polling e pela leitura de todas as páginas. */
class SdkAthenaQueryExecutor(
    private val client: AthenaClient,
    private val properties: AthenaProperties,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : AthenaQueryExecutor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(sql: String): List<Map<String, String?>> {
        val started = nanoTime()
        var executionId: String? = null
        var terminal = false

        fun remaining(): Duration {
            val nanos = properties.queryTimeout.toNanos() - (nanoTime() - started)
            if (nanos <= 0) throw AthenaUnavailableException("Tempo limite da consulta Athena excedido")
            return Duration.ofNanos(nanos)
        }

        fun requestTimeout(): AwsRequestOverrideConfiguration = AwsRequestOverrideConfiguration.builder()
            .apiCallTimeout(remaining())
            .build()

        try {
            val request = StartQueryExecutionRequest.builder()
                .queryString(sql)
                .queryExecutionContext(QueryExecutionContext.builder()
                    .database(properties.database).catalog(properties.catalog).build())
                .workGroup(properties.workgroup)
                .overrideConfiguration(requestTimeout())
            if (properties.outputLocation.isNotBlank()) {
                request.resultConfiguration(ResultConfiguration.builder()
                    .outputLocation(properties.outputLocation).build())
            }
            executionId = client.startQueryExecution(request.build()).queryExecutionId()
            if (executionId.isNullOrBlank()) throw AthenaUnavailableException("Athena não retornou o identificador da consulta")

            while (true) {
                val status = client.getQueryExecution(GetQueryExecutionRequest.builder()
                    .queryExecutionId(executionId).overrideConfiguration(requestTimeout()).build())
                    .queryExecution().status()
                when (status.state()) {
                    QueryExecutionState.SUCCEEDED -> { terminal = true; break }
                    QueryExecutionState.FAILED, QueryExecutionState.CANCELLED -> {
                        terminal = true
                        throw AthenaUnavailableException("Consulta Athena $executionId terminou em ${status.state()}")
                    }
                    QueryExecutionState.QUEUED, QueryExecutionState.RUNNING ->
                        sleep(minOf(properties.pollInterval.toMillis(), remaining().toMillis()).coerceAtLeast(1))
                    else -> throw AthenaUnavailableException("Estado Athena desconhecido para $executionId")
                }
            }

            val rows = mutableListOf<Map<String, String?>>()
            var token: String? = null
            var firstPage = true
            val seenTokens = mutableSetOf<String>()
            do {
                val result = client.getQueryResults(GetQueryResultsRequest.builder()
                    .queryExecutionId(executionId).nextToken(token).maxResults(1000)
                    .overrideConfiguration(requestTimeout()).build())
                val columns = result.resultSet().resultSetMetadata().columnInfo().map { it.name() }
                if (columns.isEmpty() || columns.distinct().size != columns.size) {
                    throw AthenaUnavailableException("Metadados Athena inválidos")
                }
                // SELECT do Athena inclui cabeçalho somente na primeira página.
                val pageRows = if (firstPage) result.resultSet().rows().drop(1) else result.resultSet().rows()
                pageRows.forEach { row ->
                    if (rows.size >= properties.maxRows) {
                        throw AthenaUnavailableException("Resultado Athena excede o limite configurado")
                    }
                    if (row.data().size != columns.size) throw AthenaUnavailableException("Linha Athena inválida")
                    rows += columns.mapIndexed { index, name -> name to row.data()[index].varCharValue() }.toMap()
                }
                token = result.nextToken()?.takeIf { it.isNotBlank() }
                if (token != null && !seenTokens.add(token)) throw AthenaUnavailableException("Paginação Athena inválida")
                firstPage = false
                remaining()
            } while (token != null)
            return rows
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AthenaUnavailableException("Consulta Athena interrompida", exception)
        } catch (exception: AthenaUnavailableException) {
            throw exception
        } catch (exception: Exception) {
            throw AthenaUnavailableException("Falha ao consultar Athena", exception)
        } finally {
            if (executionId != null && !terminal) {
                // Cancelamento best-effort também em timeout/interrupção, sem esconder a falha original.
                val interrupted = Thread.interrupted()
                try {
                    client.stopQueryExecution(StopQueryExecutionRequest.builder()
                        .queryExecutionId(executionId)
                        .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofSeconds(2)).build())
                        .build())
                } catch (exception: Exception) {
                    log.warn("Não foi possível cancelar consulta Athena {}", executionId, exception)
                } finally {
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }
}
