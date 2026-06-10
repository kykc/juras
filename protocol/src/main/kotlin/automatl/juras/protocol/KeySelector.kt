package automatl.juras.protocol

import kotlin.random.Random

/**
 * Picks the per-message key byte. The cipher requires the key's low nibble to be
 * neither 14 nor 15 (otherwise SPECIAL framing values could appear in the key
 * byte itself). [random] is injectable so tests can be deterministic.
 */
object KeySelector {
    fun pick(random: Random = Random.Default): Int {
        while (true) {
            val k = random.nextInt(256)
            if ((k and 0xF) != 14 && (k and 0xF) != 15) return k
        }
    }
}
