package io.github.belkaalyenka

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.github.belkaalyenka.mqtt.MqttService

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

        routing {
            routes()
        }
    }.start(wait = true)
}
