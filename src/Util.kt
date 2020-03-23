package de.moeri

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.response.respondText
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate

fun Int.toBoolean(): Boolean {
   return this == 1
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
