package de.moeri

import io.ktor.application.*
import io.ktor.config.ApplicationConfig
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.ContentType
import org.slf4j.event.*
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate
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
                call.respondTwig("submit", mapOf("date" to "Wie wär's mit dem 1. August?"))
            }
            post("/") {
                if (!call.request.isMultipart())
                    call.respond(HttpStatusCode.Forbidden)
                else
                    call.receiveMultipart().readAllParts().map {
                        when (it) {
                            is PartData.FormItem -> it.name to it.value
                            is PartData.FileItem -> it.name to it.originalFileName
                            else -> {
                                println("Forbidden PartData")
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }
                        }
                    }.toMap().apply {
                        try {
                            if (!containsKey("type")) {
                                println("Invalid Type")
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
                                        source = if (type == Type.HISTORIC) get("source")!! else "",
                                        date = Date.valueOf(get("date")!!),
                                        description = get("description")!!,
                                        name = if (containsKey("name")) get("name")!! else ""
                                    )
                                    Database.insert(entry)
                                    call.respondRedirect("/success")
                                } else {
                                    println("Invalid Other")
                                    call.respond(HttpStatusCode.Forbidden)
                                }

                            }
                        } catch (e: IllegalArgumentException) {
                            println("Illegal Args")
                            call.respond(HttpStatusCode.Forbidden)
                        } catch (e: NullPointerException) {
                            println("Nullpointer")
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

private val templates = mutableMapOf<String, JtwigTemplate>()

suspend fun ApplicationCall.respondTwig(template: String, model: Map<String, Any> = mapOf()) {
    if (!templates.containsKey(template))
        templates[template] = JtwigTemplate.classpathTemplate("template/$template.twig")
    val twigModel = JtwigModel.newModel()
    model.forEach { (key, value) ->
        twigModel.with(key, value)
    }
    respondText(
        templates[template]!!.render(twigModel),
        ContentType.Text.Html
    )
}

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
