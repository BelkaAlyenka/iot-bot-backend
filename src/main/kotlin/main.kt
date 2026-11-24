package io.github.belkaalyenka

import io.github.belkaalyenka.mqtt.MqttService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStarted) {
            MqttService.start()
        }
        monitor.subscribe(ApplicationStopping) {
            MqttService.stop()
        }

        install(ContentNegotiation) {
            json()
        }

        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = 1024 * 1024L
            masking = false
        }

        routing {
            routes()
        }
    }.start(wait = true)
}

