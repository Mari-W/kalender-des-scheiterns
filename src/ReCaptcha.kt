package de.moeri

import io.ktor.client.HttpClient
import io.ktor.client.request.post

object ReCaptcha {
    val httpClient = HttpClient()

    suspend fun recaptchaValidate(token: String) {
        httpClient.post<String>("https://www.google.com/recaptcha/api/siteverify") {

        }
    }
}