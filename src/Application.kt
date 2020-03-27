package de.moeri

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.request.isMultipart
import io.ktor.request.path
import io.ktor.request.receiveMultipart
import io.ktor.request.userAgent
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.slf4j.event.Level
import java.io.File
import java.lang.Exception
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
            call.respondRedirect("info")
        }
        get("info") {
            call.respondTwig("info")
        }
        get("status") {
            call.respondTwig("status", mapOf("dates" to Database.dates()))
        }
        get("events/{state?}") {
            call.respondTwig(
                "events", mapOf(
                    "dates" to Database.listEvents(),
                    "message" to when (call.parameters["state"]) {
                        "success" -> "Dein Ereignis wurde erfolgreich eingetragen!<br>Schau, was der*die anderen für Einträge gemacht haben. Sie werden chronologisch nach Ereignisdatum angezeigt."
                        "limit" -> "Du kannst maxmimal 10 Ereignisse pro Tag eintragen!<br>Stattdessen kannst du dir die Einträge von anderen anschauen. Sie werden chronologisch nach Ereignisdatum angezeigt."
                        else -> "Hier könnt ihr die eingereichten Ereignisse ansehen. Sie werden chronologisch nach Ereignisdatum angezeigt."
                    }
                )
            )
        }
        get("submit_personal") {
            call.respondTwig("submit_pers")
        }
        get("submit_historic") {
            call.respondTwig("submit_hist")
        }
        post("submit") {
            if (!call.request.isMultipart())
                call.respond(HttpStatusCode.Forbidden.description("Oh boy, tryin' to upload different forms?"))
            else
                call.receiveMultipart().readAllParts().map {
                    when (it) {
                        is PartData.FormItem -> {
                            it.name to it.value
                        }
                        else -> {
                            call.respond(HttpStatusCode.Forbidden.description("Sorry, we only take texts :/"))
                            return@post
                        }
                    }
                }.toMap().map {
                    it.key to it.value.replace("<", "(").replace(">", ")")
                }.toMap().apply {
                    try {
                        if (containsKey("g-recaptcha-response")) {
                            ReCaptcha.validate(get("g-recaptcha-response")!!)
                        } else {
                            call.respond(HttpStatusCode.Forbidden.description("U ROBOT!!!"))
                            return@post
                        }
                        if (!containsKey("type")) {
                            call.respond(HttpStatusCode.Forbidden.description("Invalid type, only historic or personal allowed."))
                            return@post
                        }
                        val type: Type? = try {
                            Type.valueOf(get("type")!!.toUpperCase())
                        } catch (e: Exception) {
                            null
                        }
                        if (containsKeys("description", "date") && type != null) {
                            val entry = Entry(
                                type = type,
                                source = if (type == Type.HISTORIC) get("source") ?: "" else "",
                                date = Date.valueOf(get("date")!!),
                                description = get("description")!!,
                                name = get("name") ?: "",
                                email = get("email") ?: ""
                            )
                            when {
                                Database.insert(
                                    call.request.origin.remoteHost,
                                    entry
                                ) -> call.respondRedirect("events/success")
                                else -> call.respondRedirect("events/limit")
                            }
                        } else {
                            call.respond(HttpStatusCode.Forbidden.description("Errör"))
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.Forbidden.description("Invalid arguments"))
                    } catch (e: NullPointerException) {
                        call.respond(HttpStatusCode.Forbidden.description("Null,null"))
                    } catch (e: ReCaptcha.CaptchaException) {
                        call.respondRedirect("/events/limit")
                    }
                }
        }


        get("/imprint") {
            call.respondTwig("imprint", mobile = false)
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
                    ),
                    mobile = false
                )
            }
            get("/download") {
                ExcelWriter.gen()
                call.response.header("Content-Disposition", "attachment; filename=\"kds-chosen-events-export.xlsx\"")
                call.respondFile(File("kds-chosen-events-export.xlsx"))
            }
            route("/edit") {
                post("/status") {
                    if (!call.request.isMultipart())
                        call.respond(HttpStatusCode.Forbidden.description("Oh boy, tryin' to upload different forms?"))
                    else
                        call.receiveMultipart().readAllParts().map {
                            when (it) {
                                is PartData.FormItem -> it.name to it.value
                                else -> {
                                    call.respond(HttpStatusCode.Forbidden.description("Sorry, we only take texts :/"))
                                    return@post
                                }
                            }
                        }.toMap().apply {
                            if (containsKey("id") && containsKey("status") && containsKey("order") && containsKey("state"))
                                try {
                                    Database.changeStatus(get("id")!!.toInt(), get("status")!!)
                                    call.respondRedirect("/mod/show/${get("state")}/sortby/${get("order")}#${get("id")}")
                                } catch (e: IllegalArgumentException) {
                                    call.respond(HttpStatusCode.Forbidden.description("Invalid arguments"))
                                }
                            else call.respond(HttpStatusCode.Forbidden.description("Errör"))
                        }
                }
            }
        }

        static("/static") {
            resources("static")
        }
    }
}
