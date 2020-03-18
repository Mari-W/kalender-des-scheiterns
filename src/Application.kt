package de.moeri

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.content.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import java.sql.Date

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@UseExperimental(KtorExperimentalAPI::class)
fun Application.module() {

    Config.init(environment.config)
    Database.init()

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
                call.respondTwig("submit", mapOf("date" to "Uns fehlt noch der 1. August!"))
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

                                call.respondRedirect("/success")
                            } catch (e: IllegalArgumentException) {
                                call.respond(HttpStatusCode.Forbidden)
                            }
                        else call.respond(HttpStatusCode.Forbidden)
                    }
            }
        }

        get("/success") {
            call.respondTwig("success")
        }

        route("/mod") {
            get("/{order?}") {
                if(call.parameters.contains("order")){
                    call.respondTwig("mod", mapOf("list" to Database.listBy(call.parameters["order"]!!), "order" to call.parameters["order"]!!))
                } else {
                    call.respondRedirect("/mod/date")
                }
            }
            route("/edit") {
                post("/status/") {
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
                            if (containsKey("id") && containsKey("status") && containsKey("order"))
                                try {
                                    Database.changeStatus(get("id")!!.toInt(), get("status")!!)
                                    call.respondRedirect("/mod/${get("order")}")
                                } catch (e: IllegalArgumentException) {
                                    call.respond(HttpStatusCode.Forbidden)
                                }
                            else call.respond(HttpStatusCode.Forbidden)
                        }
                }
            }
        }

        static("/static") {
            resources("static")
        }
    }
}
