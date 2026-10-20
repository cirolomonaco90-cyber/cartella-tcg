package it.cartella.tcg.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.data.CARD_LANGUAGES
import it.cartella.tcg.data.Condition
import it.cartella.tcg.data.LanguagePrices
import it.cartella.tcg.data.Valuation
import it.cartella.tcg.data.languageOf
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val lang by vm.language.collectAsState()
    val factors by vm.factors.collectAsState()
    val owned by vm.owned.collectAsState()
    val summary by vm.langSummary.collectAsState()
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Text(
                "Impostazioni",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Lingua e valutazione delle tue carte",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ------------------------------------------------------------ lingua
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "Lingua predefinita delle carte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    CARD_LANGUAGES.forEach { l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setLanguage(l.code) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = l.code == lang, onClick = { vm.setLanguage(l.code) })
                            Text(l.display)
                        }
                    }
                }
            }

            // ------------------------------------------------------------ valore per condizione
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Valore per condizione",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Percentuale del prezzo di riferimento applicata a ogni condizione. " +
                            "Cambiala come preferisci: il valore della collezione si aggiorna subito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Condition.entries.forEach { c ->
                        val saved = factors.of(c)
                        var local by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("${c.code} · ${c.labelIt}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        c.hintIt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${(local * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = local,
                                onValueChange = { local = it },
                                onValueChangeFinished = { vm.setFactor(c, Valuation.round2(local.toDouble())) },
                                valueRange = 0.05f..1.25f,
                            )
                        }
                    }
                    TextButton(onClick = vm::resetFactors) { Text("Ripristina valori predefiniti") }
                }
            }

            // ------------------------------------------------------------ prezzi per lingua (sola lettura)
            val langs = remember(lang, owned) { (listOf("it", lang) + owned.map { it.lang }).distinct() }
            LaunchedEffect(langs) { vm.loadLangSummary(langs) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Prezzi per lingua",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Calcolati ogni notte, in automatico: per ogni carta si confrontano le offerte CardTrader " +
                            "nella lingua con quelle in inglese, e il rapporto si applica al prezzo Cardmarket. " +
                            "Per ogni set c'è un rapporto medio specifico. Una lingua viene corretta solo se " +
                            "CardTrader ha abbastanza offerte per un confronto affidabile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    langs.forEach { code ->
                        val data = summary[code]
                        val line = when {
                            LanguagePrices.fileLang(code) == null -> "non ancora coperta"
                            !summary.containsKey(code) -> "caricamento…"
                            data == null -> "dati non disponibili"
                            else -> buildString {
                                append(
                                    when {
                                        code == "en" -> "riferimento"
                                        data.coefAll != null ->
                                            "${(data.coefAll.ratio * 100).roundToInt()}% dell'inglese (su ${data.coefAll.samples} carte)"
                                        else -> "poche offerte: nessuna correzione"
                                    },
                                )
                                append(" · ${data.prices.size} carte con offerte")
                                data.updated?.take(10)?.split("-")?.takeIf { it.size == 3 }?.let {
                                    append(" · aggiornati il ${it[2]}/${it[1]}")
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(languageOf(code).display, fontWeight = FontWeight.SemiBold)
                            Text(
                                line,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------ informazioni e TCGdex
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dati e prezzi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Carte, lingue e immagini arrivano da TCGdex, database aperto delle carte Pokémon. " +
                            "I prezzi sono le medie di mercato Cardmarket (EUR) e, se mancano, TCGplayer (USD), " +
                            "così come le fornisce TCGdex: affidabili ma uguali per tutte le lingue. " +
                            "I prezzi per lingua arrivano dalle offerte CardTrader, elaborate ogni notte. " +
                            "Nella scheda di ogni carta trovi il dettaglio del calcolo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { uri.openUri("https://tcgdex.dev") }) {
                        Text("Apri TCGdex (tcgdex.dev)")
                    }
                    OutlinedButton(onClick = { uri.openUri("https://tcgdex.dev/status") }) {
                        Text("Stato del servizio e lingue disponibili")
                    }
                    Text(
                        "Cartella TCG · versione 0.1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
