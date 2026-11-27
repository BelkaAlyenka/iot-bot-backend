package io.github.belkaalyenka.audio

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Метаданные сохранённой записи */
data class SavedAudio(
    val fileName: String,
    val path: Path,
    val durationSec: Double,
    val sizeBytes: Int
)

/**
 * Хранит сохранённые записи на диске (папка ./audio в корне проекта).
 * Пока в памяти + на диске; в Слое 7 переедет в PostgreSQL + S3.
 */
object AudioStorage {

    private val logger = LoggerFactory.getLogger(AudioStorage::class.java)
    private val dir = Path.of("audio")
    private val files = CopyOnWriteArrayList<SavedAudio>()

    val latest: SavedAudio?
        get() = files.toList().lastOrNull()

    fun save(audio: AssembledAudio): SavedAudio {
        Files.createDirectories(dir)

        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val name = "audio_${System.currentTimeMillis()}_$uniqueSuffix.wav"
        val path = dir.resolve(name)

        val wavBytes = WavWriter.build(audio)
        Files.write(path, wavBytes)

        val saved = SavedAudio(name, path, audio.durationSec, wavBytes.size)
        files.add(saved)
        logger.info("💾 Аудио сохранено: $name (${saved.sizeBytes} байт, %.1f сек)".format(saved.durationSec))
        return saved
    }
}
