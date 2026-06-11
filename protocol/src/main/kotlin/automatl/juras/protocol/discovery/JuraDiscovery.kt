package automatl.juras.protocol.discovery

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** A machine found on the local network via UDP discovery. */
data class DiscoveredMachine(
    val host: String,
    val name: String,
    val serial: String,
    val firmware: String,
)

/**
 * Finds JURA machines on the local network by UDP broadcast on port 51515.
 *
 * A 16-byte `0010A5F3…` scan packet is broadcast; each machine replies (unicast)
 * with a frame carrying its name/serial/firmware, the source address being the
 * machine's IP. Because replies are unicast back to us, no multicast lock or
 * extra permission is required.
 *
 * Pure JVM (uses `java.net`), so it stays in `:protocol`. Call off the main thread.
 */
object JuraDiscovery {

    private const val PORT = 51515

    // 00 10 A5 F3 + 12 zero bytes (total length 0x0010, magic A5F3).
    private val SCAN_PAYLOAD: ByteArray =
        byteArrayOf(0x00, 0x10, 0xA5.toByte(), 0xF3.toByte()) + ByteArray(12)

    fun discover(timeoutMs: Int = 3000): List<DiscoveredMachine> {
        val found = LinkedHashMap<String, DiscoveredMachine>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(PORT))

            for (target in broadcastTargets()) {
                runCatching {
                    socket.send(DatagramPacket(SCAN_PAYLOAD, SCAN_PAYLOAD.size, target, PORT))
                }
            }

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                socket.soTimeout = remaining.toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                parse(packet)?.let { found[it.host] = it }
            }
        }
        return found.values.toList()
    }

    private fun parse(packet: DatagramPacket): DiscoveredMachine? {
        val data = packet.data.copyOf(packet.length)
        // Jura reply: bytes 0-1 = length, 2-3 = magic A5F3; long enough to hold info.
        // This also filters our own 16-byte broadcast echo.
        if (data.size < 68 || data[2] != 0xA5.toByte() || data[3] != 0xF3.toByte()) return null
        val host = packet.address.hostAddress ?: return null
        return DiscoveredMachine(
            host = host,
            name = ascii(data, 4, 20),
            serial = ascii(data, 20, 52),
            firmware = ascii(data, 52, 68),
        )
    }

    /** Decode an ASCII field, dropping NUL padding and surrounding whitespace. */
    private fun ascii(data: ByteArray, from: Int, to: Int): String =
        String(data, from, to - from, Charsets.US_ASCII)
            .trim { it.code == 0 || it.isWhitespace() }

    /** Subnet-directed broadcast addresses of all up, non-loopback interfaces, plus 255.255.255.255. */
    private fun broadcastTargets(): List<InetAddress> {
        val targets = LinkedHashSet<InetAddress>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { targets.add(it) }
                }
            }
        }
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        return targets.toList()
    }
}
