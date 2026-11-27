package io.github.belkaalyenka.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Строит WAV-файл: 44-байтный RIFF-заголовок + сырой PCM (16-bit mono).
 * Формат WAV требует Little-Endian порядок байтов — отсюда ByteOrder.LITTLE_ENDIAN.
 */
object WavWriter {

    private const val HEADER_SIZE = 44

    fun build(audio: AssembledAudio): ByteArray {
        val pcm = audio.pcm
        val rate = audio.sampleRate

        require(rate > 0) { "sampleRate должен быть больше 0" }
        require(pcm.size <= Int.MAX_VALUE - HEADER_SIZE) {
            "Размер PCM слишком велик для стандартного WAV-заголовка"
        }

        // Выделяем один общий буфер сразу под заголовок и данные,
        // чтобы избежать лишнего копирования массива через `+`
        val buffer = ByteBuffer.allocate(HEADER_SIZE + pcm.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcm.size)      // размер файла минус 8 байт
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)                 // размер fmt-блока
        buffer.putShort(1)                // формат: 1 = PCM (без сжатия)
        buffer.putShort(1)                // каналов: 1 (моно)
        buffer.putInt(rate)               // частота дискретизации
        buffer.putInt(rate * 2)           // байт в секунду (sampleRate * channels * bits/8)
        buffer.putShort(2)                // блок выравнивания (channels * bits/8)
        buffer.putShort(16)               // бит на семпл
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcm.size)           // размер самих аудиоданных
        buffer.put(pcm)                   // записываем сам звук следом за заголовком

        return buffer.array()
    }
}
