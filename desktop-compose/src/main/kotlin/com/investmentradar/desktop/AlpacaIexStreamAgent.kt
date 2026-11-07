package com.investmentradar.desktop

import com.investmentradar.desktop.data.NativeBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * Persistent JVM-side transport only. The shared native C++ Monitor is the sole
 * evaluator and persistence authority. No Compose screen or chart is involved.
 *
 * No streaming, credential access or network work occurs without the existing
 * local IR_MONITOR_ALPACA_ENABLED=1 opt-in. This uses IEX ONLY, never paid SIP.
 */
internal class AlpacaIexStreamAgent(private val bridge: NativeBridge) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    @Volatile private var clockOpen = false
    @Volatile private var clockExpires = 0L

    internal fun enabled(): Boolean =
        System.getenv("IR_MONITOR_ALPACA_ENABLED") == "1" &&
        System.getenv("IR_MONITOR_TRANSPORT") != "http"

    private suspend fun status(value: String) {
        runCatching {
            bridge.post("/api/monitor/stream-status",
                buildJsonObject { put("status", value) }.toString())
        }
    }

    private suspend fun selection(): IexSubscriptions {
        val snapshot = Json.parseToJsonElement(bridge.get("/api/monitor")).jsonObject
        return subscriptionsFor(snapshot)
    }

    private suspend fun updateClock(key: String, secret: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI("https://paper-api.alpaca.markets/v2/clock"))
                    .timeout(Duration.ofSeconds(10))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .GET().build()
                val reply = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (reply.statusCode() != 200) {
                    clockExpires = 0L
                    false
                } else {
                    val doc = Json.parseToJsonElement(reply.body()).jsonObject
                    val open = doc["is_open"]?.jsonPrimitive?.booleanOrNull ?: false
                    clockOpen = open
                    clockExpires = System.currentTimeMillis() + 90_000L
                    true
                }
            } catch (_: Exception) {
                clockExpires = 0L
                false
            }
        }

    suspend fun run() {
        if (!enabled()) return
        val key = System.getenv("IR_MONITOR_ALPACA_KEY").orEmpty()
        val secret = System.getenv("IR_MONITOR_ALPACA_SECRET").orEmpty()
        if (key.isBlank() || secret.isBlank()) {
            status("permission-denied")
            return
        }
        var failures = 0
        while (currentCoroutineContext().isActive) {
            try {
                val wanted = selection()
                if (wanted.size == 0) {
                    status("idle")
                    delay(5_000)
                    continue
                }
                if (wanted.size > 30) {
                    status("limit-exceeded")
                    delay(30_000)
                    continue
                }
                status("connecting")
                val signals = Channel<String>(Channel.BUFFERED)
                val frames = Channel<JsonObject>(512)
                var socket: WebSocket? = null
                val listener = object : WebSocket.Listener {
                    private val buffer = StringBuilder()
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                    }
                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        try {
                            buffer.append(data)
                            if (buffer.length > 256_000) {
                                signals.trySend("stream-overflow")
                                webSocket.abort()
                            } else if (last) {
                                val payload = Json.parseToJsonElement(buffer.toString()).jsonArray
                                buffer.setLength(0)
                                for (part in payload) {
                                    val obj = part.jsonObject
                                    when (obj["T"]?.jsonPrimitive?.contentOrNull) {
                                        "success" -> signals.trySend(obj["msg"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                        "subscription" -> signals.trySend("subscribed")
                                        "error" -> {
                                            signals.trySend("permission-denied")
                                            webSocket.abort()
                                        }
                                        "q", "t" -> {
                                            if (frames.trySend(obj).isFailure) {
                                                signals.trySend("stream-overflow")
                                                webSocket.abort()
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            buffer.setLength(0)
                            signals.trySend("stream-error")
                            webSocket.abort()
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }
                    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                        signals.trySend("disconnected")
                        return CompletableFuture.completedFuture(null)
                    }
                    override fun onError(webSocket: WebSocket, error: Throwable) {
                        signals.trySend("disconnected")
                    }
                }
                try {
                    socket = withContext(Dispatchers.IO) {
                        http.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .buildAsync(URI("wss://stream.data.alpaca.markets/v2/iex"), listener)
                            .get(15, TimeUnit.SECONDS)
                    }
                    val connectedSocket = socket
                    val consumer = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                        for (part in frames) {
                            try {
                                val kind = part["T"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val event = buildJsonObject {
                                    put("kind", kind)
                                    put("data", part)
                                    // A fresh authenticated broker clock is mandatory; receipt
                                    // of stream data does not prove the regular session is open.
                                    put("marketOpen", clockOpen &&
                                        System.currentTimeMillis() < clockExpires)
                                }
                                bridge.post("/api/monitor/ingest", event.toString())
                            } catch (_: CancellationException) {
                                throw
                            } catch (_: Exception) {
                                signals.trySend("backend-error")
                                break
                            }
                        }
                    }
                    try {
                        var authenticated = false
                        var subscribedQuotes = emptySet<String>()
                        var subscribedTrades = emptySet<String>()
                        var lastClock = 0L
                        var lastPing = 0L
                        while (currentCoroutineContext().isActive) {
                            while (true) {
                                val signal = signals.tryReceive().getOrNull() ?: break
                                when (signal) {
                                    "connected" -> send(connectedSocket,
                                        buildJsonObject {
                                            put("action", "auth")
                                            put("key", key)
                                            put("secret", secret)
                                        }.toString())
                                    "authenticated" -> authenticated = true
                                    "subscribed" -> status("subscribed")
                                    "permission-denied" -> {
                                        status("permission-denied")
                                        return // never retry entitlement/auth errors automatically
                                    }
                                    "stream-overflow" -> {
                                        status("stream-overflow")
                                        error("Inbound quote buffer exceeded")
                                    }
                                    "disconnected", "stream-error", "backend-error" ->
                                        error("IEX stream disconnected")
                                }
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastClock >= 60_000) {
                                if (!updateClock(key, secret)) status("clock-unavailable")
                                lastClock = now
                            }
                            if (authenticated) {
                                val current = selection()
                                if (current.size == 0 || current.size > 30) {
                                    status(if (current.size > 30) "limit-exceeded" else "idle")
                                    break
                                }
                                val removedQuotes = subscribedQuotes - current.quotes
                                val removedTrades = subscribedTrades - current.trades
                                if (removedQuotes.isNotEmpty() || removedTrades.isNotEmpty()) {
                                    send(connectedSocket, command("unsubscribe", removedQuotes, removedTrades))
                                }
                                val newQuotes = current.quotes - subscribedQuotes
                                val newTrades = current.trades - subscribedTrades
                                if (newQuotes.isNotEmpty() || newTrades.isNotEmpty()) {
                                    send(connectedSocket, command("subscribe", newQuotes, newTrades))
                                }
                                subscribedQuotes = current.quotes
                                subscribedTrades = current.trades
                            }
                            if (now - lastPing >= 30_000) {
                                withContext(Dispatchers.IO) {
                                    connectedSocket.sendPing(java.nio.ByteBuffer.wrap(byteArrayOf(1)))
                                        .get(10, TimeUnit.SECONDS)
                                }
                                lastPing = now
                            }
                            delay(5_000)
                        }
                    } finally {
                        frames.close()
                        consumer.cancelAndJoin()
                    }
                } finally {
                    socket?.sendClose(WebSocket.NORMAL_CLOSURE, "finished")
                    socket?.abort()
                    signals.close()
                    frames.close()
                }
                failures = 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures++
                status("disconnected")
                val wait = min(300_000L, 5_000L * (1L shl min(failures, 6)))
                delay(wait + Random.nextLong(0, 2_000))
            }
        }
    }

    private suspend fun send(socket: WebSocket, body: String) {
        withContext(Dispatchers.IO) {
            socket.sendText(body, true).get(10, TimeUnit.SECONDS)
        }
    }
}

internal data class IexSubscriptions(val quotes: Set<String>, val trades: Set<String>) {
    val size: Int get() = (quotes + trades).size
}

internal fun subscriptionsFor(snapshot: JsonObject): IexSubscriptions {
    val available = snapshot["instruments"]?.jsonArray?.mapNotNull { element ->
        val row = element.jsonObject
        val symbol = row.text("id")
        if (row.text("source") == "alpaca-iex" &&
            row.text("currency") == "USD" &&
            symbol.matches(Regex("[A-Z0-9.-]{1,32}"))) symbol else null
    }?.toSet().orEmpty()
    val rules = snapshot["rules"]?.jsonArray.orEmpty().map { it.jsonObject }
        .filter { it["armed"]?.jsonPrimitive?.booleanOrNull == true }
    return IexSubscriptions(
        rules.filter { it.text("basis") == "ASK" }
            .map { it.text("instrument") }.filter { it in available }.toSortedSet(),
        rules.filter { it.text("basis") == "LAST" }
            .map { it.text("instrument") }.filter { it in available }.toSortedSet()
    )
}

internal fun command(action: String, quotes: Set<String>, trades: Set<String>): String =
    buildJsonObject {
        put("action", action)
        put("quotes", JsonArray(quotes.sorted().map(::JsonPrimitive)))
        put("trades", JsonArray(trades.sorted().map(::JsonPrimitive)))
    }.toString()
