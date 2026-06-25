package automatl.juras.ui

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal const val TCP_OPERATION_ATTEMPTS = 3
internal const val TCP_OPERATION_RETRY_DELAY_MS = 2_000L

internal fun isRetriableTcpFailure(throwable: Throwable): Boolean = throwable !is UnknownHostException

internal suspend fun <T> retryTcpOperation(
    attempts: Int = TCP_OPERATION_ATTEMPTS,
    delayMs: Long = TCP_OPERATION_RETRY_DELAY_MS,
    shouldRetry: (Throwable) -> Boolean = ::isRetriableTcpFailure,
    block: suspend () -> T,
): T {
    require(attempts > 0) { "attempts must be positive" }

    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!shouldRetry(e) || attempt == attempts - 1) throw e
            delay(delayMs)
        }
    }

    error("retry loop exited without returning or throwing")
}
