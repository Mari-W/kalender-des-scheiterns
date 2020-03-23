package de.moeri

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.response.respondText
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate
import java.io.File
import java.security.MessageDigest

object HashUtils {

    fun sha256(input: String) = hashString("SHA-256", input)


    private fun hashString(type: String, input: String): String {
        val HEX_CHARS = "0123456789ABCDEF"
        val bytes = MessageDigest
            .getInstance(type)
            .digest(input.toByteArray())
        val result = StringBuilder(bytes.size * 2)

        bytes.forEach {
            val i = it.toInt()
            result.append(HEX_CHARS[i shr 4 and 0x0f])
            result.append(HEX_CHARS[i and 0x0f])
        }

        return result.toString()
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
