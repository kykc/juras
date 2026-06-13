package automatl.juras.ui.platform

import androidx.compose.runtime.Composable

/** Returns a lambda that opens a file-open picker; the chosen file's text (or null on failure/cancel) is delivered to [onText]. */
@Composable
expect fun rememberOpenFileLauncher(onText: (String?) -> Unit): () -> Unit

/** Returns a lambda that opens a file-save picker; [content] is called lazily to produce bytes. [onDone] receives true on success. */
@Composable
expect fun rememberSaveFileLauncher(
    suggestedName: String,
    content: () -> ByteArray,
    onDone: (Boolean) -> Unit,
): () -> Unit
