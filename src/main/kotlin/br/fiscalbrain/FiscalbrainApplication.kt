package br.fiscalbrain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FiscalbrainApplication

fun main(args: Array<String>) {
    runApplication<FiscalbrainApplication>(*args)
}

