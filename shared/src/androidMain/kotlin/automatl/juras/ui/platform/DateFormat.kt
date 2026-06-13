package automatl.juras.ui.platform

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

actual fun formatClock(millis: Long): String {
    val d = Date(millis)
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return if (millis >= todayStart) clockFormat.format(d)
    else "${dateFormat.format(d)} ${clockFormat.format(d)}"
}
