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
                        try {
                            if (!containsKey("type")) {
                                call.respond(HttpStatusCode.Forbidden)
                            } else {
                                val type: Type? = Type.valueOf(get("type")!!.toUpperCase())
                                val okay = when (type) {
                                    Type.PERSONAL -> containsKey("description") && containsKey("date")
                                    Type.HISTORIC -> containsKey("description") && containsKey("date") && containsKey("source")
                                     else -> false
                                }
                                if (okay && type != null) {
                                    val entry = Entry(
                                        type = type,
                                        source = if (type==Type.HISTORIC) get("source")!! else "",
                                        date = Date.valueOf(get("date")!!),
                                        description = get("description")!!,
                                        name = if (containsKey("name")) get("name")!! else ""
                                    )
                                    Database.insert(entry)
                                    call.respondRedirect("/success")
                                } else {
                                    call.respond(HttpStatusCode.Forbidden)
                                }

                            }
                        } catch (e: IllegalArgumentException) {
                            call.respond(HttpStatusCode.Forbidden)
                        } catch (e: NullPointerException) {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
            }
        }

        get("/success") {
            call.respondTwig("success")
        }

        route("/mod") {
            get("/") {
                call.respondRedirect("/mod/show/pending/sortby/date")
            }
            get("/show/{status?}/sortby/{order?}") {
                val status = if (call.parameters.contains("status")) call.parameters["status"] else "pending"
                val order = if (call.parameters.contains("order")) call.parameters["order"] else "date"
                call.respondTwig(
                    "mod",
                    mapOf(
                        "list" to Database.list(status!!, order!!),
                        "order" to order,
                        "status" to status
                    )
                )
            }
            route("/edit") {
                post("/status") {
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
                            if (containsKey("id") && containsKey("status") && containsKey("order") && containsKey("state"))
                                try {
                                    Database.changeStatus(get("id")!!.toInt(), get("status")!!)
                                    call.respondRedirect("/mod/show/${get("state")}/sortby/${get("order")}")
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
