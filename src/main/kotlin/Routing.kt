package io.github.belkaalyenka

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.github.belkaalyenka.mqtt.MqttService

fun Route.routes() {

    get("/health") {
        call.respond(mapOf("status" to "OK"))
    }

    post("/api/led") {
        try {
            val request = call.receive<LedRequest>()
            val normalizedState = request.state.lowercase()

            if (normalizedState !in listOf("on", "off")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("State must be 'on' or 'off'")
                )
                return@post
            }

            MqttService.publishLedState(normalizedState)
            call.respond(LedResponse("success", normalizedState))

        } catch (e: BadRequestException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid JSON format: ${e.message}")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Server error: ${e.message}")
            )
        }
    }
}
