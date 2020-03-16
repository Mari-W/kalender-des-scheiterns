package de.moeri

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.content.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.auth.*
import io.ktor.gson.*
import io.ktor.util.KtorExperimentalAPI

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@UseExperimental(KtorExperimentalAPI::class)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    Config.init(environment.config)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(Authentication) {
        basic("moderator") {
            validate { if (it.name == "test" && it.password == "password") UserIdPrincipal(it.name) else null }
        }
    }

    install(ContentNegotiation) { gson {} }

    routing {
        get("/") {
            call.respondTwig("submit", mapOf("var" to Config["password"]))
        }

        static("/static") {
            resources("static")
        }

        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}
