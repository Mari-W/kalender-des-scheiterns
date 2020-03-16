package de.moeri

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
object Config {

    lateinit var conf: ApplicationConfig

    fun init(config: ApplicationConfig) {
        conf = config
    }

    operator fun get(key: String): String {
        return conf.propertyOrNull("kds.$key")?.getString() ?: ""
    }
}