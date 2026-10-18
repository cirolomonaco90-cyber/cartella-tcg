package it.cartella.tcg

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import it.cartella.tcg.ui.CartellaApp
import it.cartella.tcg.ui.CartellaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Header con gradiente scuro: le icone della barra di stato restano sempre chiare.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            CartellaTheme {
                val vm: AppViewModel = viewModel()
                CartellaApp(vm)
            }
        }
    }
}
