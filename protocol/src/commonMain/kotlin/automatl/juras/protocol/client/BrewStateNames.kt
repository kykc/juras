package automatl.juras.protocol.client

/**
 * Human-readable captions for the brew/progress state byte (`@tv:` byte 0).
 *
 * State codes and their names derived from protocol reverse engineering and hardware
 * verification on JURA E6 / EF1030. `0x24` is "Coffee ready" and `0x3E` is "Enjoy".
 * A couple of codes (`0x39` grinding, `0x40` fill-water-during-operation) were
 * observed on hardware and added empirically.
 *
 * Active **dispensing** states are not caption states — they are each product's
 * dispensing state value ([DISPENSING_STATES]); when the state is one of those the
 * client reports live water progress instead of a caption.
 */
object BrewStateNames {

    /** Dispensing/active-pour states (per-product `ProgressAdjust` values, EF1030). */
    val DISPENSING_STATES: Set<Int> = setOf(0x34, 0x37, 0x3C, 0x41)

    fun nameFor(code: Int): String? = NAMES[code]

    private val NAMES: Map<Int, String> = mapOf(
        0x01 to "Insert tray",
        0x02 to "Fill water tank",
        0x03 to "Empty grounds",
        0x04 to "Empty tray",
        0x08 to "Insert grounds box",
        0x09 to "Close nozzle cover",
        0x0A to "Close bean cover",
        0x0B to "Cappu rinse finished",
        0x0C to "Close tap",
        0x0D to "Open tap",
        0x0E to "Alert",
        0x0F to "Close powder cover",
        0x10 to "Add ground coffee",
        0x11 to "Filling system",
        0x12 to "Emptying system",
        0x13 to "Add beans",
        0x14 to "Not enough powder",
        0x15 to "Please wait",
        0x16 to "Remove water tank",
        0x20 to "Starting up",
        0x21 to "Heating up",
        0x22 to "Press button",
        0x23 to "Rinsing",
        0x24 to "Coffee ready",
        0x25 to "Shutting down",
        0x26 to "Press rinse",
        0x2D to "No milk",
        0x2E to "Milk error",
        0x2F to "No milk signal",
        0x39 to "Grinding", // not in XML; observed (jura.py)
        0x3E to "Enjoy",
        0x40 to "Fill water tank", // not in XML; observed on hardware
        0x4A to "Cappu clean requested",
        0x50 to "Descale: start",
        0x51 to "Descale: prepare materials",
        0x52 to "Descale: empty tray",
        0x53 to "Descale: add fluid",
        0x54 to "Descaling",
        0x55 to "Rinse water tank",
        0x56 to "Descaling finished",
        0x5A to "Connect milk tube",
        0x5C to "Filter used up",
        0x5D to "Filter locked",
        0x60 to "Filter rinse: start",
        0x61 to "Filter rinse: prepare materials",
        0x62 to "Filter rinse: change",
        0x63 to "Filter rinsing",
        0x65 to "Filter rinse finished",
        0x66 to "Remove filter",
        0x67 to "Insert filter",
        0x70 to "Cleaning: start",
        0x71 to "Cleaning: prepare materials",
        0x72 to "Cleaning: empty tray",
        0x73 to "Cleaning: press button",
        0x74 to "Cleaning",
        0x75 to "Cleaning: add tablet",
        0x76 to "Cleaning finished",
        0x90 to "Cappu clean: start",
        0x91 to "Cappu clean: prepare materials",
        0x92 to "Cappu clean: add cleaner",
        0x93 to "Cappu cleaning",
        0x94 to "Cappu clean: add water",
        0x95 to "Cappu clean finished",
        0x9A to "Cappu rinsing",
        0xA1 to "Set hot-water nozzle",
        0xA2 to "Set milk nozzle",
        0xA3 to "Set nozzle to milk position",
        0xA4 to "Set nozzle to foam position",
        0xC7 to "Long-press saved",
        0xC9 to "Enough milk/water/coffee",
        0xE1 to "Warning",
        0xE2 to "Action required",
        0xE3 to "Info",
        0xE4 to "Filter error",
        0xE5 to "Filter thanks",
        0xE6 to "Too hot",
        0xEF to "Wi-Fi confirmation",
        0xFF to "Programming mode",
    )
}
