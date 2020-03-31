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

    suspend fun validate(token: String) {
        val res = httpClient.post<RecaptchaResponse>("https://www.google.com/recaptcha/api/siteverify") {
            parameter("secret", Config["recaptcha.secret"])
            parameter("response", token)
            //parameter("remoteip", IP_HERE_WATCH_OUT_FOR_CLOUDFLARE)
        }

        if (res.errorCodes != null && res.errorCodes.isNotEmpty()) {
            throw CaptchaException("ReCaptcha Error: ${res.errorCodes.joinToString(", ")}")
        }
        if (!res.success) {
            throw CaptchaException("No success")
        }
        if (res.score < 0.5) {
            throw CaptchaException("ReCaptcha too low with ${res.score}")
        }
    }

    data class RecaptchaResponse(
        val success: Boolean,
        val score: Double,
        val action: String,
        @SerializedName("challenge_ts") val challengeTs: String,
        val hostname: String,
        @SerializedName("erros-codes") val errorCodes: Array<String>?)

    class CaptchaException(s: String) : Exception(s)
}