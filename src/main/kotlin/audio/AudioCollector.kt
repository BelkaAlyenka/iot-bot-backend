package io.github.belkaalyenka.audio

import org.slf4j.LoggerFactory

/**
 * Ведёт текущую аудиосессию: следит, что сначала был start,
 * потом кусочки, потом end. Защищает от «осколков» чужих сессий.
 */
object AudioCollector {

    private val logger = LoggerFactory.getLogger(AudioCollector::class.java)
    private var current: AudioAssembler? = null

    @Synchronized
    fun onStart(sampleRate: Int) {
        if (current != null) {
            logger.warn("⚠️ Новый start пришёл до end предыдущей сессии — старая отброшена")
        }
        current = AudioAssembler(sampleRate)
        logger.info("🎙️ Начата аудиосессия (частота: $sampleRate Гц)")
    }

    @Synchronized
    fun onData(chunk: ByteArray) {
        val assembler = current
        if (assembler == null) {
            logger.warn("⚠️ Кусочек аудио без активной сессии — игнорируем (${chunk.size} байт)")
            return
        }
        assembler.append(chunk)
    }

    @Synchronized
    fun onEnd(): SavedAudio? {
        val assembler = current ?: run {
            logger.warn("⚠️ end без активной сессии — игнорируем")
            return null
        }
        current = null

        if (assembler.sizeBytes == 0) {
            logger.warn("⚠️ Сессия завершена без аудиоданных (0 байт) — не сохраняем")
            return null
        }

        val audio = assembler.finish()
        logger.info("✅ Сессия завершена: получено чанков — ${assembler.receivedChunks}")
        return AudioStorage.save(audio)
    }
}
