package automatl.juras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import automatl.juras.ui.JurasApp
import automatl.juras.ui.theme.JurasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JurasTheme {
                JurasApp()
            }
        }
    }
}
