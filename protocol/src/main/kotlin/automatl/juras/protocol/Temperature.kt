package automatl.juras.protocol

import kotlinx.serialization.Serializable

/** Brew temperature. [wire] is the value used in the `@TP:` payload (field F7). */
@Serializable
enum class Temperature(val wire: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
}
