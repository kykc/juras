package automatl.juras.protocol.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Read-only **UDP** status poller (port 51515), mirroring J.O.E.'s live-status channel.
 *
 * J.O.E. polls machine status/progress over UDP, *not* the TCP command socket: about
 * once a second it unicasts a 16-byte datagram `0010A5F3 <machine-IP> 00000000_00000000`
 * and the machine replies with a plaintext status packet that embeds the same
 * `@TV:`/`@TF:` payload the TCP channel would push (see [UdpStatusReply]). Because UDP
 * is connectionless this lets several clients watch status without contending for the
 * machine's single TCP session, and keeps TCP light — which this firmware strongly
 * prefers. See CLAUDE.md §5 ("JOE streams live status/progress over UDP").
 *
 * This client performs a single request/response poll. Blocking — run off the main
 * thread (the Android layer wraps it in `Dispatchers.IO`).
 */
class JuraUdpStatusClient(
    private val host: String,
    private val port: Int = JuraConnection.DEFAULT_PORT,
    private val timeoutMs: Int = 2_000,
) {

    /** Send one status poll and parse the reply. Throws on I/O error or timeout. */
    fun poll(): UdpStatusReply {
        val machine = InetAddress.getByName(host)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val request = buildPollPacket(machine.address)
            socket.send(DatagramPacket(request, request.size, machine, port))
            val buf = ByteArray(MAX_REPLY)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            return UdpStatusReply.parse(buf.copyOf(packet.length))
        }
    }

    companion object {
        private const val MAX_REPLY = 2048

        /** The 16-byte status poll: `0010A5F3` + machine IPv4 + 8 zero bytes. */
        fun buildPollPacket(machineIpv4: ByteArray): ByteArray {
            require(machineIpv4.size == 4) { "need an IPv4 address, got ${machineIpv4.size} bytes" }
            return byteArrayOf(0x00, 0x10, 0xA5.toByte(), 0xF3.toByte()) +
                machineIpv4 +
                ByteArray(8)
        }
    }
}
