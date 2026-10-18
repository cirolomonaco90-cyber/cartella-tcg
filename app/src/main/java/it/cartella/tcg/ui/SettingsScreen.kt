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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.data.CARD_LANGUAGES
import it.cartella.tcg.data.Condition
import it.cartella.tcg.data.PriceEngine
import it.cartella.tcg.data.Valuation
import it.cartella.tcg.data.languageOf
import kotlin.math.roundToInt

@Composable
private fun CardTraderCard(vm: AppViewModel) {
    val saved by vm.cardTraderToken.collectAsState()
    var token by remember(saved) { mutableStateOf(saved) }
    var visible by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Prezzi per lingua (CardTrader)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Tag(
                    if (saved.isBlank()) "Non collegato" else "Collegato",
                    container = if (saved.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer,
                    content = if (saved.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                "CardTrader è un marketplace europeo che mostra le offerte filtrate per lingua della carta. " +
                    "Con il tuo token personale (gratuito) l'app legge le offerte nella lingua di ogni tua copia " +
                    "e le combina con Cardmarket.\n\nCome ottenerlo: crea un account su cardtrader.com, apri " +
                    "le impostazioni del profilo, sezione API, copia il token e incollalo qui. " +
                    "Il token resta solo su questo telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token API CardTrader") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) { Text(if (visible) "Nascondi" else "Mostra") }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveCardTraderToken(token) }, enabled = token.trim() != saved) {
                    Text("Salva e verifica")
                }
                if (saved.isNotBlank()) {
                    TextButton(onClick = {
                        token = ""
                        vm.saveCardTraderToken("")
                    }) { Text("Scollega") }
                }
            }
            TextButton(onClick = { uri.openUri("https://www.cardtrader.com") }) { Text("Apri cardtrader.com") }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val lang by vm.language.collectAsState()
    val factors by vm.factors.collectAsState()
    val owned by vm.owned.collectAsState()
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

            // ------------------------------------------------------------ CardTrader
            CardTraderCard(vm)

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

            // ------------------------------------------------------------ correzione per lingua
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Correzione per lingua",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Il prezzo Cardmarket è uguale per tutte le lingue. Quando CardTrader ha poche offerte " +
                            "per una carta, l'app usa Cardmarket × questa percentuale. Con almeno " +
                            "${PriceEngine.MIN_LEARN} carte con offerte CardTrader in una lingua, la percentuale " +
                            "viene calcolata da sola dalla tua collezione; altrimenti vale quella impostata qui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Lingue presenti in collezione, più l'italiano e quella predefinita.
                    val langs = (listOf("it", lang) + owned.map { it.lang }).distinct()
                    langs.forEach { code ->
                        val saved = factors.manualLang(code)
                        val learned = factors.learned[code]
                        var local by remember(code, saved) { mutableFloatStateOf(saved.toFloat()) }
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(languageOf(code).display, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${(local * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            when {
                                learned != null && learned.samples >= PriceEngine.MIN_LEARN -> Text(
                                    "In uso: ${(learned.ratio * 100).roundToInt()}%, calcolato da CardTrader " +
                                        "su ${learned.samples} carte (la percentuale manuale è ignorata)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                learned != null -> Text(
                                    "Dati CardTrader su ${learned.samples} carte: ne servono almeno ${PriceEngine.MIN_LEARN}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = local,
                                onValueChange = { local = it },
                                onValueChangeFinished = {
                                    vm.setLanguageFactor(code, Valuation.round2(local.toDouble()))
                                },
                                valueRange = 0.3f..2.0f,
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
                            "I prezzi per lingua arrivano dalle offerte CardTrader (se collegato) e dal " +
                            "coefficiente di lingua. Nella scheda di ogni carta trovi il dettaglio del calcolo.",
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
