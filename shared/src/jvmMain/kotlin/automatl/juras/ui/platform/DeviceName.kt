package automatl.juras.ui.platform

import java.net.InetAddress

actual val defaultDeviceName: String =
    runCatching { InetAddress.getLocalHost().hostName }
        .getOrNull()
        ?.substringBefore('.')
        ?.takeIf { it.isNotBlank() && it != "localhost" }
        ?: System.getProperty("user.name")
        ?: "Desktop"
