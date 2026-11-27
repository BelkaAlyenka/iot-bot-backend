package io.github.belkaalyenka.ws

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import io.github.belkaalyenka.ws.event.WsEvent
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArraySet

object WebSocketHub {

    private val logger = LoggerFactory.getLogger(WebSocketHub::class.java)
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    val clientCount: Int
        get() = sessions.size

    fun add(session: WebSocketSession) {
        sessions.add(session)
        logger.info("➕ WS-клиент подключился. Всего клиентов: ${sessions.size}")
    }

    fun remove(session: WebSocketSession) {
        if (sessions.remove(session)) {
            logger.info("➖ WS-клиент отключился. Всего клиентов: ${sessions.size}")
        }
    }

    /**
     * Параллельная рассылка типизированного события всем клиентам с защитой от зависаний.
     */
    suspend fun broadcast(event: WsEvent) = coroutineScope {
        val message = event.toJson()

        sessions.forEach { session ->
            launch {
                try {
                    withTimeout(3000) {
                        session.send(message)
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warn("⏱️ Клиент не отвечает (таймаут 3с), удаляем из хаба.")
                    remove(session)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("⚠️ Ошибка отправки клиенту, удаляем: ${e.message}")
                    remove(session)
                }
            }
        }
    }
}
