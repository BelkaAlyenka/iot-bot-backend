package io.github.belkaalyenka.tools

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * Симулятор ESP32 для Слоя 3.
 * Запуск без аргументов: шлёт сгенерированный гудок 3 сек.
 * Запуск с аргументом: шлёт указанный WAV-файл (путь к .wav).
 */
fun main(args: Array<String>) {
    val prefix = "iot_bot_demo_7842"
    val broker = "tcp://broker.hivemq.com:1883"

    val (rate, pcm) = if (args.isNotEmpty()) {
        readWav(Path.of(args[0]))
    } else {
        16000 to generateBeep(16000, 3.0)
    }

    val chunkSize = 1000
    val chunks = (pcm.size + chunkSize - 1) / chunkSize

    val client = MqttClient(broker, MqttClient.generateClientId(), MemoryPersistence())
    try {
        client.connect(MqttConnectOptions().apply { isCleanSession = true })
        println("📡 Симулятор подключился к брокеру")

        fun publish(topic: String, payload: ByteArray) {
            client.publish(topic, MqttMessage(payload).apply { qos = 1 })
        }

        val startJson = """{"type":"start","rate":$rate,"chunks":$chunks}"""
        publish("$prefix/audio/meta", startJson.toByteArray(Charsets.UTF_8))
        println("🎙️ Отправляю $chunks кусочков (${pcm.size} байт)...")

        for (i in 0 until chunks) {
            val from = i * chunkSize
            val to = minOf(from + chunkSize, pcm.size)
            publish("$prefix/audio/data", pcm.copyOfRange(from, to))
            Thread.sleep(5) // пауза между кусочками
        }

        publish("$prefix/audio/meta", """{"type":"end"}""".toByteArray(Charsets.UTF_8))
        println("✅ Готово: отправлено $chunks кусочков")
    } finally {
        if (client.isConnected) client.disconnect()
        client.close()
    }
}

/** Генерирует синусоидальный гудок (440 Гц) — тестовый «голос» */
private fun generateBeep(rate: Int, seconds: Double): ByteArray {
    val n = (rate * seconds).toInt()
    val buf = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n) {
        val sample = (sin(2.0 * PI * 440 * i / rate) * 12000).toInt().toShort()
        buf.putShort(sample)
    }
    return buf.array()
}

/** Читает WAV-файл любой длины заголовка: находит sample rate и блок "data" */
private fun readWav(path: Path): Pair<Int, ByteArray> {
    require(Files.exists(path)) { "Файл не найден: $path" }

    val bytes = Files.readAllBytes(path)
    require(bytes.size >= 44) { "Файл слишком мал для WAV: ${bytes.size} байт" }

    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val rate = bb.getInt(24) // смещение 24 = sample rate в fmt-чанке

    // Ищем чанк "data", так как перед ним могут быть метаданные (LIST/INFO и т.д.)
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
        val chunkSize = bb.getInt(pos + 4)
        if (chunkId == "data") {
            val dataStart = pos + 8
            val dataEnd = minOf(dataStart + chunkSize, bytes.size)
            val pcm = bytes.copyOfRange(dataStart, dataEnd)
            println("📄 Прочитан файл: $rate Гц, ${pcm.size} байт PCM")
            return rate to pcm
        }
        pos += 8 + chunkSize + (chunkSize % 2) // в WAV чанки выравниваются по чётному байту
    }

    error("В файле $path не найден аудио-блок 'data'")
}
