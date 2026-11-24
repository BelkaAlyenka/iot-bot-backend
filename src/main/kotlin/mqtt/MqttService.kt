package io.github.belkaalyenka.mqtt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object MqttService {

    private const val BROKER_URL = "tcp://broker.hivemq.com:1883"

    private const val TOPIC_PREFIX = "iot_bot_demo_7842"
    private const val TOPIC_LED = "$TOPIC_PREFIX/led"
    private const val TOPIC_BUTTON = "$TOPIC_PREFIX/button"

    private val clientId = MqttClient.generateClientId()
    private lateinit var client: MqttClient

    fun start() {
        client = MqttClient(BROKER_URL, clientId, MemoryPersistence())

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                val status = if (reconnect) "Переподключено" else "Подключено"
                println("✅ $status к MQTT брокеру ($serverURI)")
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                println("⚠️ Связь с MQTT потеряна: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: ""
                println("📩 Получено от ESP32 (топик: $topic): $payload")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 20
        }

        println("🔌 Подключаемся к MQTT брокеру: $BROKER_URL")
        client.connect(options)
    }

    private fun subscribeToTopics() {
        client.subscribe(TOPIC_BUTTON, 1)
        println("📡 Подписались на топик: $TOPIC_BUTTON")
    }

    suspend fun publishLedState(state: String) = withContext(Dispatchers.IO) {
        if (!::client.isInitialized || !client.isConnected) {
            throw IllegalStateException("MQTT клиент не подключен к брокеру")
        }

        val jsonPayload = """{"state": "$state"}"""
        val message = MqttMessage(jsonPayload.toByteArray()).apply {
            qos = 1
        }

        client.publish(TOPIC_LED, message)
        println("📤 Отправлено в топик $TOPIC_LED: $jsonPayload")
    }

    fun stop() {
        if (!this::client.isInitialized) return

        try {
            if (client.isConnected) {
                client.disconnect()
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка при отключении MQTT: ${e.message}")
        } finally {
            try {
                client.close()
                println("✅ MQTT клиент корректно остановлен")
            } catch (e: Exception) {
                println("⚠️ Ошибка при закрытии ресурсов MQTT: ${e.message}")
            }
        }
    }

}