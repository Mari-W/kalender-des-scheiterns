package de.moeri

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.content.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.css.h1
import kotlinx.css.html
import kotlinx.css.p
import kotlinx.html.body
import kotlinx.html.h1
import java.lang.Exception
import java.sql.Date

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@UseExperimental(KtorExperimentalAPI::class)
fun Application.module() {

    Config.init(environment.config)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    routing {

        get("/") {
            call.respondRedirect("/submit")
        }

        route("/submit") {
            get("/") {
                call.respondTwig("submit")
            }
            post("/") {
                if (!call.request.isMultipart())
                    call.respond(HttpStatusCode.Forbidden)
                else
                    call.receiveMultipart().readAllParts().map {
                        when (it) {
                            is PartData.FormItem -> it.name to it.value
                            else -> {
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }
                        }
                    }.toMap().apply {
                        if (containsKey("text") && containsKey("date") && containsKey("source"))
                            try {
                                DB.insertIdea(Idea(get("text")!!, Date.valueOf(get("date")!!), get("source")!!))
                                call.respondRedirect("/submit")
                            } catch (e: IllegalArgumentException) {
                                call.respond(HttpStatusCode.Forbidden)
                            }
                        else  call.respond(HttpStatusCode.Forbidden)
                    }
            }
        }
        get("/todo") {
            call.respondHtml {
                body {
                    h1 {
                        text("reCAPTCHA integration fertig stellen")
                    }
                    h1 {
                        text("optional contact field in submit")
                    }
                    h1 {
                        text("front page?")
                    }
                    h1 {
                        text("submit success page?")
                    }
                    h1 {
                        text("moderator page (kds-topsecret.moehritz.de) funktionalität integrieren")
                    }
                }
            }
        }

        route("/mod") {
            get("/") {
                call.respondTwig("list", mapOf("list" to DB.listIdeas()))
            }
        }

        static("/static") {
            resources("static")
        }
    }
}
