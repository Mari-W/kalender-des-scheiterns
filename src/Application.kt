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
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.css.h1
import kotlinx.html.body
import kotlinx.html.h1
import java.lang.Exception
import java.sql.Date

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
            call.respondHtml {
                body {
                    h1 {
                        text("Front page :)")
                    }
                }
            }
        }

        route("/submit") {
            get("/") {
                call.respondTwig("submitn")
            }
            post("/") {
                if (!call.request.isMultipart())
                    call.respond(HttpStatusCode.Forbidden)
                call.receiveMultipart().readAllParts().map {
                    when (it) {
                        is PartData.FormItem -> it.name to it.value
                        else -> {
                            "" to ""
                        }
                    }
                }.toMap().apply {
                    if (containsKey("text") && containsKey("date") && containsKey("source"))
                        try {
                            insertIdea(Idea(get("text")!!, Date.valueOf(get("date")!!), get("source")!!))
                            call.respondRedirect("/success")
                        } catch (e: IllegalArgumentException) {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    else  call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        static("/static") {
            resources("static")
        }

        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}
