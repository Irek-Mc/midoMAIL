package midomail.platform.jvm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Serwer WebSocket testowy — MINIMALNA implementacja RFC 6455, identyczna do
 * `:adapter-websocket`'s `TestWebSocketServer` (Iteracja 7.5). Duplikat świadomy — test source
 * sets nie są współdzielone między modułami Gradle; ten sam wzorzec co `AdapterRegistryOutbound`
 * (ADR-0036).
 */
class TestWebSocketServer {
    private val serverSocket = ServerSocket(0)
    private val executor = Executors.newCachedThreadPool()
    private val connections = CopyOnWriteArrayList<Connection>()
    val receivedMessages = ConcurrentLinkedQueue<String>()

    val port: Int get() = serverSocket.localPort
    val url: String get() = "ws://localhost:$port/"

    fun start() {
        executor.submit { acceptLoop() }
    }

    fun stop() {
        serverSocket.close()
        connections.forEach { it.close() }
        executor.shutdownNow()
    }

    fun broadcastText(text: String) {
        connections.forEach { it.sendText(text) }
    }

    fun activeConnectionCount(): Int = connections.size

    private fun acceptLoop() {
        try {
            while (!serverSocket.isClosed) {
                val socket = serverSocket.accept()
                val connection = Connection(socket, receivedMessages)
                connections.add(connection)
                executor.submit {
                    connection.readLoop()
                    connections.remove(connection)
                }
            }
        } catch (_: Exception) {
            // serverSocket zamknięty przez stop() - koniec pętli akceptacji, oczekiwane
        }
    }

    private class Connection(
        private val socket: Socket,
        private val receivedMessages: ConcurrentLinkedQueue<String>
    ) {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())

        init {
            performHandshake()
        }

        private fun performHandshake() {
            val requestLines = generateSequence { readLine() }.takeWhile { it.isNotEmpty() }.toList()
            val webSocketKey = requestLines
                .firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()
                ?: error("Brak nagłówka Sec-WebSocket-Key w żądaniu handshake")

            val acceptValue = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
            )

            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptValue\r\n\r\n"
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        private fun readLine(): String {
            val builder = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte == -1 || byte == '\n'.code) break
                if (byte != '\r'.code) builder.append(byte.toChar())
            }
            return builder.toString()
        }

        fun readLoop() {
            try {
                while (!socket.isClosed) {
                    val frame = readFrame() ?: break
                    when (frame.opcode) {
                        0x1 -> receivedMessages.add(String(frame.payload, Charsets.UTF_8))
                        0x8 -> { close(); break }
                        0x9 -> sendFrame(0xA, frame.payload)
                    }
                }
            } catch (_: Exception) {
                // gniazdo zamknięte w trakcie odczytu - oczekiwane przy rozłączeniu
            }
        }

        private data class Frame(val opcode: Int, val payload: ByteArray)

        private fun readFrame(): Frame? {
            val byte0 = input.read()
            if (byte0 == -1) return null
            val opcode = byte0 and 0x0F
            val byte1 = input.read()
            if (byte1 == -1) return null
            val masked = (byte1 and 0x80) != 0
            var length = (byte1 and 0x7F).toLong()
            if (length == 126L) {
                length = ((input.read().toLong() shl 8) or input.read().toLong())
            } else if (length == 127L) {
                length = 0
                repeat(8) { length = (length shl 8) or input.read().toLong() }
            }
            val maskKey = if (masked) ByteArray(4).also { input.readFully(it) } else null
            val payload = ByteArray(length.toInt()).also { input.readFully(it) }
            if (maskKey != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }
            return Frame(opcode, payload)
        }

        fun sendText(text: String) = sendFrame(0x1, text.toByteArray(Charsets.UTF_8))

        private fun sendFrame(opcode: Int, payload: ByteArray) {
            synchronized(output) {
                output.write(0x80 or opcode)
                when {
                    payload.size < 126 -> output.write(payload.size)
                    payload.size < 65536 -> {
                        output.write(126)
                        output.write((payload.size shr 8) and 0xFF)
                        output.write(payload.size and 0xFF)
                    }
                    else -> error("Ładunek zbyt duży dla tego minimalnego serwera testowego")
                }
                output.write(payload)
                output.flush()
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
                // już zamknięte - bez znaczenia dla testu
            }
        }
    }
}
