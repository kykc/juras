package automatl.juras.ui.platform

/** Formats [millis] as HH:mm:ss (today) or "d MMM HH:mm:ss" (older). */
expect fun formatClock(millis: Long): String
