package de.moeri

import com.google.gson.annotations.SerializedName
import io.ktor.client.HttpClient
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
object ReCaptcha {
    private val httpClient = HttpClient {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    suspend fun recaptchaValidate(token: String) {
        val res = httpClient.post<RecaptchaResponse>("https://www.google.com/recaptcha/api/siteverify") {
            parameter("secret", Config["recaptcha.secret"])
            parameter("response", token)
            //parameter("remoteip", IP_HERE_WATCH_OUT_FOR_CLOUDFLARE)
        }

        if (res.errorCodes.isNotEmpty()) {
            println("ReCaptcha Error: ${res.errorCodes.joinToString(", ")}")
            throw CaptchaException
        }
        if (!res.success) {
            println("ReCaptcha invalid")
            throw CaptchaException
        }
        if (res.score < 0.5) {
            println("ReCaptcha too low with ${res.score}")
            throw CaptchaException
        }
        println("Successful recaptcha with score of ${res.score}")

    }

    data class RecaptchaResponse(
        val success: Boolean,
        val score: Double,
        val action: String,
        @SerializedName("challenge_ts") val challengeTs: String,
        val hostname: String,
        @SerializedName("erros-codes") val errorCodes: Array<String>) {}

    object CaptchaException : Exception("captcha score too low")
}