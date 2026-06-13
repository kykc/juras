package automatl.juras

import java.io.File

internal fun configDir(): File {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    val home = System.getProperty("user.home") ?: "."
    return when {
        os.contains("mac") -> File(home, "Library/Application Support/Juras")
        os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Juras")
        else -> File(home, ".config/juras")
    }
}
