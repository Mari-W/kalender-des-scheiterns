package de.moeri

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
object Config {

    private lateinit var conf: ApplicationConfig

    fun init(config: ApplicationConfig) {
        conf = config
    }

    operator fun get(key: String): String {
        return conf.propertyOrNull(key)?.getString() ?: "Aua"
    }

}
