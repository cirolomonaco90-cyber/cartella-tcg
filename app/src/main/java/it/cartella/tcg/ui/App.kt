package it.cartella.tcg.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun CartellaApp(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val detail by vm.detail.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Messaggi di conferma ("Pikachu aggiunta alla collezione", ...).
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    Scaffold(
        // Le testate con gradiente gestiscono da sole lo spazio sotto la barra di stato.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Cerca") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Collezione") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Impostazioni") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    0 -> SearchScreen(vm, onOpenCard = vm::openCard)
                    1 -> CollectionScreen(vm, onOpen = vm::openOwned, onGoSearch = { tab = 0 })
                    else -> SettingsScreen(vm)
                }
            }
        }
    }

    if (detail.open) {
        CardDetailSheet(vm, onDismiss = vm::closeDetail)
    }
}
