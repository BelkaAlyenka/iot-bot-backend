package io.github.belkaalyenka.ws.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Базовый класс для всех WebSocket-событий.
 * Поле "type" добавляется в JSON автоматически благодаря classDiscriminator = "type"
 * и аннотациям @SerialName на наследниках.
 */
@Serializable
sealed class WsEvent {

    /** Превращает событие в JSON-строку для отправки клиенту */
    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        /** Парсит входящую JSON-строку обратно в нужный подкласс WsEvent */
        fun fromJson(json: String): WsEvent = jsonFormat.decodeFromString(json)
    }
}

/**
 * Событие: на ESP32 нажата кнопка.
 * Пример JSON: {"type":"BUTTON_PRESSED","device":"esp32-hall","timestamp":1710000000}
 */
@Serializable
@SerialName("BUTTON_PRESSED")
data class ButtonPressedEvent(
    val device: String,
    val timestamp: Long = System.currentTimeMillis()
) : WsEvent()

/**
 * Событие: фактическое состояние светодиода на ESP32.
 * Пример JSON: {"type":"LED_STATE","state":"on","timestamp":1710000000}
 */
@Serializable
@SerialName("LED_STATE")
data class LedStateEvent(
    val state: String, // "on" или "off"
    val timestamp: Long = System.currentTimeMillis()
) : WsEvent()

/**
 * Событие: ошибка или информационное сообщение.
 * Пример JSON: {"type":"SERVER_MESSAGE","level":"INFO","message":"Connected"}
 */
@Serializable
@SerialName("SERVER_MESSAGE")
data class ServerMessageEvent(
    val level: String, // "INFO", "WARN", "ERROR"
    val message: String
) : WsEvent()

/**
 * Единый JSON-форматтер.
 */
private val jsonFormat = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
