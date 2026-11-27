package io.github.belkaalyenka.mqtt

import io.github.belkaalyenka.audio.AudioCollector
import io.github.belkaalyenka.ws.WebSocketHub
import io.github.belkaalyenka.ws.event.ButtonPressedEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory

object MqttService {

    private val logger = LoggerFactory.getLogger(MqttService::class.java)
    private const val BROKER_URL = "tcp://broker.hivemq.com:1883"
    private const val CONNECTION_TIMEOUT_SEC = 10
    private const val KEEP_ALIVE_INTERVAL_SEC = 20

    private const val TOPIC_PREFIX = "iot_bot_demo_7842"
    private const val TOPIC_LED = "$TOPIC_PREFIX/led"
    private const val TOPIC_BUTTON = "$TOPIC_PREFIX/button"
    private const val TOPIC_AUDIO_META = "$TOPIC_PREFIX/audio/meta"
    private const val TOPIC_AUDIO_DATA = "$TOPIC_PREFIX/audio/data"

    private val clientId = MqttClient.generateClientId()
    private lateinit var client: MqttClient

    // Канал теперь передаёт сырые байты (ByteArray), чтобы не портить бинарное аудио
    private val eventChannel = Channel<Pair<String, ByteArray>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        startEventProcessor()

        client = MqttClient(BROKER_URL, clientId, MemoryPersistence())

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                val status = if (reconnect) "Переподключено" else "Подключено"
                logger.info("✅ $status к MQTT брокеру ($serverURI)")
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                logger.warn("⚠️ Связь с MQTT потеряна: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload ?: return // сырые байты, без конвертации в String!
                if (topic == null) return

                logger.debug("📩 MQTT [$topic]: ${payload.size} байт")
                eventChannel.trySend(topic to payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = CONNECTION_TIMEOUT_SEC
            keepAliveInterval = KEEP_ALIVE_INTERVAL_SEC
        }

        logger.info("🔌 Подключаемся к MQTT брокеру: $BROKER_URL")
        try {
            client.connect(options)
        } catch (e: MqttException) {
            logger.error("❌ Не удалось подключиться к MQTT при старте (будут попытки автопереподключения): ${e.message}")
        }
    }

    private fun startEventProcessor() {
        scope.launch {
            try {
                for ((topic, payload) in eventChannel) {
                    handleMqttMessage(topic, payload)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("❌ Обработчик MQTT событий упал: ${e.message}", e)
            }
        }
    }

    private suspend fun handleMqttMessage(topic: String, payload: ByteArray) {
        try {
            when (topic) {
                TOPIC_BUTTON -> {
                    val text = String(payload, Charsets.UTF_8) // строка только для JSON!
                    val device = Json.parseToJsonElement(text).jsonObject["device"]
                        ?.jsonPrimitive?.contentOrNull ?: "unknown"

                    logger.info("🎯 Кнопка нажата на устройстве: $device")
                    WebSocketHub.broadcast(ButtonPressedEvent(device = device))
                }

                TOPIC_AUDIO_META -> {
                    val text = String(payload, Charsets.UTF_8)
                    val json = Json.parseToJsonElement(text).jsonObject
                    when (json["type"]?.jsonPrimitive?.contentOrNull) {
                        "start" -> AudioCollector.onStart(
                            json["rate"]?.jsonPrimitive?.intOrNull ?: 16000
                        )
                        "end" -> AudioCollector.onEnd()
                        else -> logger.warn("⚠️ Неизвестный тип meta-сообщения: $text")
                    }
                }

                TOPIC_AUDIO_DATA -> AudioCollector.onData(payload) // байты идут как есть

                else -> logger.debug("Неизвестный топик: $topic")
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Не удалось обработать MQTT-сообщение [$topic]: ${e.message}")
        }
    }

    private fun subscribeToTopics() {
        try {
            client.subscribe(TOPIC_BUTTON, 1)
            client.subscribe(TOPIC_AUDIO_META, 1)
            client.subscribe(TOPIC_AUDIO_DATA, 1)
            logger.info("📡 Подписались на: $TOPIC_BUTTON, $TOPIC_AUDIO_META, $TOPIC_AUDIO_DATA")
        } catch (e: MqttException) {
            logger.error("❌ Ошибка подписки: ${e.message}")
        }
    }

    suspend fun publishLedState(state: String) = withContext(Dispatchers.IO) {
        if (!::client.isInitialized || !client.isConnected) {
            throw IllegalStateException("MQTT клиент не подключен к брокеру")
        }

        val jsonPayload = """{"state": "$state"}"""
        val message = MqttMessage(jsonPayload.toByteArray(Charsets.UTF_8)).apply { qos = 1 }

        client.publish(TOPIC_LED, message)
        logger.info("📤 Отправлено в топик $TOPIC_LED (QoS=1): $jsonPayload")
    }

    fun stop() {
        scope.cancel()
        eventChannel.close()

        if (!::client.isInitialized) return
        try {
            if (client.isConnected) client.disconnect()
        } catch (e: Exception) {
            logger.warn("⚠️ Ошибка при отключении MQTT: ${e.message}")
        } finally {
            try {
                client.close()
                logger.info("✅ MQTT клиент корректно остановлен")
            } catch (e: Exception) {
                logger.warn("⚠️ Ошибка при закрытии MQTT: ${e.message}")
            }
        }
    }
}
