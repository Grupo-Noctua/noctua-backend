package br.com.noctua

import br.com.noctua.config.AthenaProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AthenaProperties::class)
class NoctuaApplication

fun main(args: Array<String>) {
    runApplication<NoctuaApplication>(*args)
}
