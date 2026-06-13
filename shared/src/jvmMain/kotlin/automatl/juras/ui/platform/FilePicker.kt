package automatl.juras.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberOpenFileLauncher(onText: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "Open configuration"
                    chooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")
                    val result = withContext(Dispatchers.Main) {
                        chooser.showOpenDialog(null)
                    }
                    if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.readText()
                    else null
                }.getOrNull()
            }
            onText(text)
        }
    }
}

@Composable
actual fun rememberSaveFileLauncher(
    suggestedName: String,
    content: () -> ByteArray,
    onDone: (Boolean) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "Save configuration"
                    chooser.selectedFile = File(suggestedName)
                    val result = withContext(Dispatchers.Main) {
                        chooser.showSaveDialog(null)
                    }
                    if (result == JFileChooser.APPROVE_OPTION) {
                        chooser.selectedFile.writeBytes(content())
                        true
                    } else false
                }.getOrElse { false }
            }
            onDone(ok)
        }
    }
}
