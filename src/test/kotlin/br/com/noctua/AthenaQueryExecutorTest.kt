package br.com.noctua

import br.com.noctua.config.AthenaProperties
import br.com.noctua.repository.AthenaUnavailableException
import br.com.noctua.repository.SdkAthenaQueryExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.*
import java.time.Duration

class AthenaQueryExecutorTest {
    private val client = mock(AthenaClient::class.java)
    private val properties = AthenaProperties(
        enabled = true, database = "noctua", workgroup = "dashboard",
        outputLocation = "s3://results/dashboard/", queryTimeout = Duration.ofMillis(10),
        pollInterval = Duration.ofMillis(1),
    )

    private fun start() {
        `when`(client.startQueryExecution(any(StartQueryExecutionRequest::class.java)))
            .thenReturn(StartQueryExecutionResponse.builder().queryExecutionId("query-123").build())
    }

    private fun state(state: QueryExecutionState) = GetQueryExecutionResponse.builder()
        .queryExecution(QueryExecution.builder().status(QueryExecutionStatus.builder().state(state).build()).build())
        .build()

    private fun page(values: List<List<String?>>, token: String? = null) = GetQueryResultsResponse.builder()
        .resultSet(ResultSet.builder()
            .resultSetMetadata(ResultSetMetadata.builder().columnInfo(
                ColumnInfo.builder().name("area").type("varchar").build(),
                ColumnInfo.builder().name("quantidade").type("bigint").build(),
            ).build())
            .rows(values.map { valuesInRow -> Row.builder()
                .data(valuesInRow.map { Datum.builder().varCharValue(it).build() }).build() })
            .build())
        .nextToken(token).build()

    private fun succeed() {
        start()
        `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java)))
            .thenReturn(state(QueryExecutionState.SUCCEEDED))
    }

    @Test
    fun `percorre estados e paginas removendo somente primeiro cabecalho`() {
        start()
        `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java)))
            .thenReturn(state(QueryExecutionState.QUEUED), state(QueryExecutionState.RUNNING), state(QueryExecutionState.SUCCEEDED))
        `when`(client.getQueryResults(any(GetQueryResultsRequest::class.java)))
            .thenReturn(
                page(listOf(listOf("area", "quantidade"), listOf("Dados", "4")), "page2"),
                page(listOf(listOf("Engenharia", "2"), listOf(null, "1"))),
            )
        val result = SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }, sleep = {}).execute("SELECT ...")
        assertThat(result).containsExactly(
            mapOf("area" to "Dados", "quantidade" to "4"),
            mapOf("area" to "Engenharia", "quantidade" to "2"),
            mapOf("area" to null, "quantidade" to "1"),
        )
        val request = ArgumentCaptor.forClass(StartQueryExecutionRequest::class.java)
        verify(client).startQueryExecution(request.capture())
        assertThat(request.value.queryExecutionContext().database()).isEqualTo("noctua")
        assertThat(request.value.workGroup()).isEqualTo("dashboard")
        assertThat(request.value.resultConfiguration().outputLocation()).isEqualTo("s3://results/dashboard/")
        val pages = ArgumentCaptor.forClass(GetQueryResultsRequest::class.java)
        verify(client, times(2)).getQueryResults(pages.capture())
        assertThat(pages.allValues.map { it.nextToken() }).containsExactly(null, "page2")
        verify(client, never()).stopQueryExecution(any(StopQueryExecutionRequest::class.java))
    }

    @Test
    fun `falha e cancelamento remoto nao tentam obter resultados`() {
        listOf(QueryExecutionState.FAILED, QueryExecutionState.CANCELLED).forEach { status ->
            reset(client)
            start()
            `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java))).thenReturn(state(status))
            assertThatThrownBy { SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }).execute("SELECT ...") }
                .isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining(status.toString())
            verify(client, never()).getQueryResults(any(GetQueryResultsRequest::class.java))
            verify(client, never()).stopQueryExecution(any(StopQueryExecutionRequest::class.java))
        }
    }

    @Test
    fun `timeout cancela a consulta mesmo se cancelamento falhar`() {
        start()
        `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java)))
            .thenReturn(state(QueryExecutionState.RUNNING))
        `when`(client.stopQueryExecution(any(StopQueryExecutionRequest::class.java)))
            .thenThrow(IllegalStateException("indisponível"))
        var now = 0L
        val executor = SdkAthenaQueryExecutor(client, properties, nanoTime = { now }, sleep = { now += 10000000 })
        assertThatThrownBy { executor.execute("SELECT ...") }
            .isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining("Tempo limite")
        verify(client).stopQueryExecution(any(StopQueryExecutionRequest::class.java))
    }

    @Test
    fun `interrupcao preserva flag e cancela consulta`() {
        start()
        `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java)))
            .thenReturn(state(QueryExecutionState.RUNNING))
        try {
            assertThatThrownBy {
                SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }, sleep = { throw InterruptedException() })
                    .execute("SELECT ...")
            }.isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining("interrompida")
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            verify(client).stopQueryExecution(any(StopQueryExecutionRequest::class.java))
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `falha SDK durante polling cancela consulta`() {
        start()
        `when`(client.getQueryExecution(any(GetQueryExecutionRequest::class.java)))
            .thenThrow(AthenaException.builder().message("access denied").statusCode(403).build())
        assertThatThrownBy { SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }).execute("SELECT ...") }
            .isInstanceOf(AthenaUnavailableException::class.java).hasCauseInstanceOf(AthenaException::class.java)
        verify(client).stopQueryExecution(any(StopQueryExecutionRequest::class.java))
    }

    @Test
    fun `limite de linhas rejeita resultados parciais`() {
        succeed()
        `when`(client.getQueryResults(any(GetQueryResultsRequest::class.java)))
            .thenReturn(page(listOf(listOf("area", "quantidade"), listOf("A", "1"), listOf("B", "2"))))
        assertThatThrownBy {
            SdkAthenaQueryExecutor(client, properties.copy(maxRows = 1), nanoTime = { 0 }).execute("SELECT ...")
        }.isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining("limite configurado")
    }

    @Test
    fun `cabecalho sem dados retorna lista vazia`() {
        succeed()
        `when`(client.getQueryResults(any(GetQueryResultsRequest::class.java)))
            .thenReturn(page(listOf(listOf("area", "quantidade"))))
        assertThat(SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }).execute("SELECT ...")).isEmpty()
    }

    @Test
    fun `timeout abrange recuperacao das paginas`() {
        succeed()
        var now = 0L
        `when`(client.getQueryResults(any(GetQueryResultsRequest::class.java))).thenAnswer {
            now = 10000000
            page(listOf(listOf("area", "quantidade"), listOf("A", "1")), "next")
        }
        assertThatThrownBy { SdkAthenaQueryExecutor(client, properties, nanoTime = { now }).execute("SELECT ...") }
            .isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining("Tempo limite")
        verify(client, times(1)).getQueryResults(any(GetQueryResultsRequest::class.java))
    }

    @Test
    fun `token repetido interrompe paginacao`() {
        succeed()
        `when`(client.getQueryResults(any(GetQueryResultsRequest::class.java)))
            .thenReturn(page(listOf(listOf("area", "quantidade")), "same"))
        assertThatThrownBy { SdkAthenaQueryExecutor(client, properties, nanoTime = { 0 }).execute("SELECT ...") }
            .isInstanceOf(AthenaUnavailableException::class.java).hasMessageContaining("Paginação")
    }
}
