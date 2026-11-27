package io.github.belkaalyenka

import io.github.belkaalyenka.mqtt.MqttService
import io.github.belkaalyenka.ws.WebSocketHub
import io.github.belkaalyenka.ws.event.LedStateEvent
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Routes")

fun Route.routes() {

    staticResources("/", "static")

    get("/health") {
        call.respond(mapOf("status" to "OK"))
    }

    post("/api/led") {
        try {
            val request = call.receive<LedRequest>()
            val normalizedState = request.state.lowercase()

            if (normalizedState !in listOf("on", "off")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("State must be 'on' or 'off'"))
                return@post
            }

            MqttService.publishLedState(normalizedState)

            WebSocketHub.broadcast(LedStateEvent(state = normalizedState))

            call.respond(LedResponse("success", normalizedState))

        } catch (e: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON format: ${e.message}"))
        } catch (e: Exception) {
            logger.error("❌ Ошибка в POST /api/led: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error: ${e.message}"))
        }
    }

    webSocket("/ws/status") {
        WebSocketHub.add(this)

        try {
            for (frame in incoming) {
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("⚠️ WS соединение прервано: ${e.message}")
        } finally {
            WebSocketHub.remove(this)
        }
    }
}

