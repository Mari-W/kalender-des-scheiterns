package de.moeri

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.config.ApplicationConfig
import io.ktor.features.CallLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.request.isMultipart
import io.ktor.request.path
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate
import org.slf4j.event.Level
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.sql.Date
import javax.imageio.ImageIO


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
                            is PartData.FileItem -> {
                                val ext = File(it.originalFileName).extension
                                val file = File("/data", "upload-${System.currentTimeMillis().hashCode()}.$ext")
                                it.streamProvider().use { input ->
                                    file.outputStream().buffered().use { output -> input.copyToSuspend(output) }
                                }
                                try {
                                    ImageIO.read(file) != null
                                } catch (e: java.lang.Exception) {
                                    println("No image")
                                    call.respond(HttpStatusCode.Forbidden)
                                }
                                "picture" to file.absolutePath
                            }
                            else -> {
                                println("Forbidden PartData")
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }
                        }
                    }.toMap().apply {
                        try {
                            if (containsKey("g-recaptcha-response")) {
                                ReCaptcha.recaptchaValidate(get("g-recaptcha-response")!!)
                            } else {
                                println("No captcha")
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }

                            if (!containsKey("type")) {
                                println("Invalid Type")
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }
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


                        } catch (e: IllegalArgumentException) {
                            println("Illegal Args")
                            call.respond(HttpStatusCode.Forbidden)
                        } catch (e: NullPointerException) {
                            println("Nullpointer")
                            call.respond(HttpStatusCode.Forbidden)
                        } catch (e: CaptchaException) {
                            call.respondRedirect("/success")
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

object CaptchaException : Exception("captcha score too low")

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

suspend fun InputStream.copyToSuspend(
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    yieldSize: Int = 4 * 1024 * 1024,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Long {
    return withContext(dispatcher) {
        val buffer = ByteArray(bufferSize)
        var bytesCopied = 0L
        var bytesAfterYield = 0L
        while (true) {
            val bytes = read(buffer).takeIf { it >= 0 } ?: break
            out.write(buffer, 0, bytes)
            if (bytesAfterYield >= yieldSize) {
                yield()
                bytesAfterYield %= yieldSize
            }
            bytesCopied += bytes
            bytesAfterYield += bytes
        }
        return@withContext bytesCopied
    }
}