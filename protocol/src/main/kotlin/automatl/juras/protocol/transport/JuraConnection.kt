package automatl.juras.protocol.transport

import automatl.juras.protocol.JuraFrame
import automatl.juras.protocol.KeySelector
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Owns the single TCP session to a JURA machine (port 51515). The machine accepts
 * only one connection at a time, so exactly one [JuraConnection] should be open
 * per machine at any moment.
 *
 * All calls are **blocking** — run them off the main thread (the Android layer
 * wraps this in `Dispatchers.IO`). Not thread-safe; serialize access externally.
 */
class JuraConnection(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : AutoCloseable {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    fun connect() {
        check(socket == null) { "already connected" }
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = readTimeoutMs
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    /** Encrypt, frame, and send a single command (e.g. `@TG:C0`). */
    fun send(command: String) {
        val out = output ?: error("not connected")
        out.write(JuraFrame.encode(command, KeySelector.pick()))
        out.flush()
    }

    /** Read the next complete frame and return the decoded command string. */
    fun receive(): String {
        val ins = input ?: error("not connected")
        val buf = ArrayList<Byte>(64)

        // Scan for the 0x2A start sentinel.
        while (true) {
            val b = readByte(ins)
            if (b == START) {
                buf.add(b.toByte())
                break
            }
        }
        // Accumulate until the literal 0x0D terminator (0x0A follows). Escape
        // pairs (0x1B, x) are copied intact so an escaped byte can never be
        // mistaken for the terminator.
        while (true) {
            val b = readByte(ins)
            buf.add(b.toByte())
            when (b) {
                CR -> {
                    buf.add(readByte(ins).toByte()) // consume trailing 0x0A
                    return JuraFrame.decode(buf.toByteArray())
                }
                ESC -> buf.add(readByte(ins).toByte())
            }
        }
    }

    private fun readByte(ins: InputStream): Int {
        val b = ins.read()
        if (b == -1) throw IOException("connection closed by machine")
        return b and 0xFF
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
            // ignore
        } finally {
            socket = null
            input = null
            output = null
        }
    }

    companion object {
        const val DEFAULT_PORT = 51515
        private const val START = 0x2A
        private const val ESC = 0x1B
        private const val CR = 0x0D
    }
}
