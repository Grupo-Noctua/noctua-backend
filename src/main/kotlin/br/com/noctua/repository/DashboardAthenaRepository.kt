package br.com.noctua.repository

import br.com.noctua.config.AthenaProperties
import br.com.noctua.dto.*
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Repository

@Repository
class DashboardAthenaRepository(
    private val executor: AthenaQueryExecutor,
    properties: AthenaProperties,
    resourceLoader: ResourceLoader,
) {
    // Apenas arquivos controlados pelo operador; nenhuma consulta é recebida por HTTP.
    private val queries: Map<String, String> = if (!properties.enabled) emptyMap() else
        listOf("total", "tecnologia", "senioridade", "area").associateWith { indicator ->
            val location = requireNotNull(properties.queries[indicator])
            require(location.startsWith("file:") || location.startsWith("classpath:")) {
                "SQL Athena deve usar recurso file: ou classpath:"
            }
            resourceLoader.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .also { require(it.isNotBlank()) { "Arquivo SQL Athena vazio: $indicator" } }
        }

    fun totalFuncionarios(): TotalDTO {
        val rows = query("total")
        if (rows.size != 1) throw AthenaUnavailableException("Consulta de total deve retornar exatamente uma linha")
        return TotalDTO(rows.single().count("total"))
    }

    fun funcionariosPorTecnologia(): List<TecnologiaDTO> = query("tecnologia").map {
        TecnologiaDTO(it.dimension("tecnologia"), it.count("quantidade"))
    }

    fun funcionariosPorSenioridade(): List<SenioridadeDTO> = query("senioridade").map {
        SenioridadeDTO(it.dimension("senioridade"), it.count("quantidade"))
    }

    fun funcionariosPorArea(): List<AreaDTO> = query("area").map {
        AreaDTO(it.dimension("area"), it.count("quantidade"))
    }

    private fun query(indicator: String): List<Map<String, String?>> {
        val sql = queries[indicator] ?: throw AthenaUnavailableException("SQL Athena não configurado: $indicator")
        return executor.execute(sql)
    }

    private fun Map<String, String?>.dimension(alias: String): String? {
        if (!containsKey(alias)) throw AthenaUnavailableException("Alias Athena ausente: $alias")
        return get(alias)
    }

    private fun Map<String, String?>.count(alias: String): Long = get(alias)?.toLongOrNull()
        ?.takeIf { it >= 0 } ?: throw AthenaUnavailableException("Contagem Athena inválida: $alias")
}
