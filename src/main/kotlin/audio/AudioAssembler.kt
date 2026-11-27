package io.github.belkaalyenka.audio

import java.io.ByteArrayOutputStream

/** Результат сборки: сырой PCM (16-bit mono) + параметры */
data class AssembledAudio(
    val pcm: ByteArray,
    val sampleRate: Int,
    val durationSec: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssembledAudio) return false
        return sampleRate == other.sampleRate &&
                durationSec == other.durationSec &&
                pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int {
        var result = pcm.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationSec.hashCode()
        return result
    }
}

/**
 * Собирает бинарные кусочки аудио (16-bit mono PCM) в один буфер.
 * Жизненный цикл: создали -> append() много раз -> finish().
 */
class AudioAssembler(private val sampleRate: Int) {

    init {
        require(sampleRate > 0) { "sampleRate должен быть больше 0, получено: $sampleRate" }
    }

    private val buffer = ByteArrayOutputStream()
    private var isFinished = false

    var receivedChunks: Int = 0
        private set

    val sizeBytes: Int
        get() = buffer.size()

    @Synchronized
    fun append(chunk: ByteArray) {
        check(!isFinished) { "Нельзя добавлять чанки после вызова finish()" }
        if (chunk.isEmpty()) return

        buffer.write(chunk)
        receivedChunks++
    }

    @Synchronized
    fun finish(): AssembledAudio {
        check(!isFinished) { "Метод finish() уже был вызван" }
        isFinished = true

        val totalBytes = buffer.size()
        check(totalBytes % 2 == 0) {
            "Некорректный размер PCM (16-bit): ожидалось чётное число байт, получено $totalBytes"
        }

        val pcmBytes = buffer.toByteArray()
        buffer.reset() // освобождаем память буфера

        return AssembledAudio(
            pcm = pcmBytes,
            sampleRate = sampleRate,
            // 16 бит моно = 2 байта на семпл
            durationSec = totalBytes / (sampleRate * 2.0)
        )
    }
}