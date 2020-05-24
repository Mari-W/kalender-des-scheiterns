package de.moeri

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CachingHeaders
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.origin
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.request.isMultipart
import io.ktor.request.receiveMultipart
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import java.io.File
import java.sql.Date
import java.util.concurrent.TimeUnit

private val applicationMp4 = ContentType("application", "mp4")
private val imageFavicon = ContentType("image", "fav")

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@UseExperimental(KtorExperimentalAPI::class)
fun Application.module() {

    Config.init(environment.config)
    Database.init()

    install(XForwardedHeaderSupport)



    install(CachingHeaders) {
        val nocache = CachingOptions(CacheControl.NoCache(CacheControl.Visibility.Public)) // do not cache the html
        val cache = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 5)) // 5 minutes
        val cacheLong = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 60)) // 1 hour
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.Html -> nocache
                ContentType.Text.CSS -> cache
                ContentType.Text.JavaScript -> cache
                applicationMp4 -> cacheLong
                imageFavicon -> cacheLong
                ContentType.Image.SVG -> cacheLong
                ContentType.Image.PNG -> cacheLong
                else -> null
            }
        }
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
                        "success" -> "Dein Ereignis wurde erfolgreich eingetragen!<br>Schau, was die anderen für Einträge gemacht haben. Sie werden chronologisch nach Ereignisdatum angezeigt (nachdem mein Team sie freigeschaltet hat)."
                        "limit" -> "Du kannst maximal 10 Ereignisse pro Tag eintragen!<br>Stattdessen kannst du dir die Einträge von anderen anschauen. Sie werden chronologisch nach Ereignisdatum angezeigt (nachdem mein Team sie freigeschaltet hat)."
                        "robot" -> "Fehler beim eintragen."
                        else -> "Hier könnt ihr die eingereichten Ereignisse ansehen. Sie werden chronologisch nach Ereignisdatum angezeigt (nachdem mein Team sie freigeschaltet hat)."
                    }
                )
            )
        }
        get("submit_personal") {
            call.respondTwig("submit_pers", mapOf("recaptcha" to true))
        }
        get("submit_historic") {
            call.respondTwig("submit_hist", mapOf("recaptcha" to true))
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
                            ReCaptcha.validate(get("g-recaptcha-response")!!, call.request.origin.remoteHost)
                        } else {
                            call.respond(HttpStatusCode.Forbidden.description("U DUMB ROBOT!!!"))
                            return@post
                        }
                        if (!containsKey("type")) {
                            call.respond(HttpStatusCode.Forbidden.description("Invalid type, only historic or personal allowed."))
                            return@post
                        }
                        val type: Type? = try {
                            Type.valueOf(get("type")!!.toUpperCase())
                        } catch (e: Exception) {
                            throw  IllegalArgumentException("Invalid submit type")
                        }
                        if (containsKeys("description", "date") && type != null) {

                            val len = get("description")!!.length
                            if (!(5 <= len || len <= 250))
                                throw IllegalArgumentException("Invalid description length")

                            val d = Date.valueOf(get("date")!!)

                            val entry = Entry(
                                type = type,
                                source = if (type == Type.HISTORIC) get("source") ?: "" else "",
                                date = Date(d.time + TimeUnit.HOURS.toMillis(3)),
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
                            throw  IllegalArgumentException("Date or description missing")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.Forbidden.description("Invalid arguments"))
                    } catch (e: NullPointerException) {
                        call.respond(HttpStatusCode.Forbidden.description("Null,null"))
                    } catch (e: ReCaptcha.CaptchaException) {
                        call.application.environment.log.info("ReCaptcha Failed for " + call.request.origin.remoteHost + " with msg " + e.message)
                        call.respondRedirect("/events/robot")
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
