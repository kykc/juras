package automatl.juras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import automatl.juras.data.AppStateRepository
import automatl.juras.ui.JurasApp
import automatl.juras.ui.theme.JurasTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy { AppStateRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JurasTheme {
                JurasApp(store = repository)
            }
        }
    }
}
