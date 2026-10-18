package it.cartella.tcg.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.LangPriceState
import it.cartella.tcg.data.CardDetail
import it.cartella.tcg.data.CardLangPrice
import it.cartella.tcg.data.Condition
import it.cartella.tcg.data.MarketLinks
import it.cartella.tcg.data.PriceEngine
import it.cartella.tcg.data.ValueFactors
import it.cartella.tcg.data.Valuation
import it.cartella.tcg.data.Variant
import it.cartella.tcg.data.condition
import it.cartella.tcg.data.languageOf
import it.cartella.tcg.data.variant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val d by vm.detail.collectAsState()
    val factors by vm.factors.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = LocalUriHandler.current

    val editing = d.editing
    val card = d.card
    val brief = d.brief

    var condition by remember(editing?.id) {
        mutableStateOf(editing?.condition ?: Condition.NEAR_MINT)
    }
    var variant by remember(editing?.id) {
        mutableStateOf(editing?.variant ?: Variant.NORMAL)
    }
    var quantity by remember(editing?.id) { mutableIntStateOf(editing?.quantity ?: 1) }

    // Varianti realmente esistenti per questa carta (se l'API non le indica, le proponiamo tutte).
    val options: List<Variant> = remember(card) {
        if (card == null) {
            emptyList()
        } else {
            buildList {
                if (card.hasNormal) add(Variant.NORMAL)
                if (card.hasReverse) add(Variant.REVERSE)
                if (card.hasHolo) add(Variant.HOLO)
                if (card.hasFirstEdition) add(Variant.FIRST_EDITION)
                if (isEmpty()) addAll(listOf(Variant.NORMAL, Variant.REVERSE, Variant.HOLO))
            }
        }
    }
    LaunchedEffect(options) {
        if (editing == null && options.isNotEmpty() && variant !in options) variant = options.first()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---------------------------------------------------------- vetrina
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BrandGradient)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardImage(
                        base = card?.imageBase ?: brief?.imageBase,
                        name = card?.name ?: brief?.name.orEmpty(),
                        high = true,
                        modifier = Modifier
                            .width(200.dp)
                            .shadow(16.dp, RoundedCornerShape(10.dp)),
                    )
                    if (d.loading) {
                        CircularProgressIndicator(Modifier.padding(top = 4.dp), color = Color.White, strokeWidth = 3.dp)
                    }
                    Text(
                        card?.name ?: brief?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    if (card != null) {
                        val number = card.setTotal?.let { "${card.localId}/$it" } ?: card.localId
                        Text(
                            "${card.setName} · $number",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                        )
                        card.rarity?.let { Tag(it, Color.White.copy(alpha = 0.22f), Color.White) }
                        card.illustrator?.let {
                            Text(
                                "Illustrazione di $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (card.dataFromEnglish) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White,
                            ) {
                                Text(
                                    "TCGdex non ha ancora questa carta in ${languageOf(d.lang).label.lowercase()}: " +
                                        "nome e immagine sono della versione inglese. " +
                                        "Puoi comunque registrarla come copia in ${languageOf(d.lang).label.lowercase()}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        } else if (card.imageFromEnglish) {
                            Text(
                                "Immagine della versione inglese (manca in questa lingua)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            d.error?.let {
                ErrorBanner(it)
                if (!it.startsWith("Carta non trovata")) {
                    TextButton(onClick = vm::retryDetail) { Text("Riprova") }
                }
            }

            // ---------------------------------------------------------- lingua
            Section("Lingua della tua copia") {
                LanguageChips(selected = d.lang, onSelect = vm::setDetailLanguage)
            }

            // ---------------------------------------------------------- condizione
            Section("Condizione") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Condition.entries.forEach { c ->
                        FilterChip(
                            selected = c == condition,
                            onClick = { condition = c },
                            label = { Text("${c.code} · ${c.labelIt}") },
                        )
                    }
                }
                Text(
                    condition.hintIt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------------------------------------------------- variante
            if (options.size > 1 || (options.isEmpty() && editing != null)) {
                Section("Variante") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val shown = if (options.isEmpty()) listOf(variant) else options
                        shown.forEach { v ->
                            FilterChip(
                                selected = v == variant,
                                onClick = { variant = v },
                                label = { Text(v.labelIt) },
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------- quantità
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Quantità",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (quantity > 1) quantity-- }) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "$quantity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { if (quantity < 999) quantity++ }) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            // ---------------------------------------------------------- valore
            ValueBox(
                card = card,
                variant = variant,
                condition = condition,
                factors = factors,
                lang = d.lang,
                langState = d.langState,
                langPrice = d.langPrice,
                quantity = quantity,
                loading = d.loading,
            )

            // ---------------------------------------------------------- verifica sul mercato
            if (card != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            uri.openUri(
                                MarketLinks.ebaySold(
                                    MarketLinks.searchText(card.name, card.localId, card.setTotal, d.lang),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Venduti su eBay") }
                    OutlinedButton(
                        onClick = { uri.openUri(MarketLinks.cardmarketSearch(card.name)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Cardmarket") }
                }
            }

            // ---------------------------------------------------------- azioni
            if (editing == null) {
                Button(
                    onClick = {
                        card?.let { vm.addToCollection(it, d.lang, condition, variant, quantity) }
                        onDismiss()
                    },
                    enabled = card != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiungi alla collezione", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = {
                        vm.updateOwned(editing, card, d.lang, condition, variant, quantity)
                        onDismiss()
                    },
                    // Non si può spostare la copia in una lingua in cui la carta non esiste.
                    enabled = card != null || (d.lang == editing.lang && !d.loading),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salva modifiche", fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = {
                        vm.deleteOwned(editing)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Elimina dalla collezione", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}


/** "2026-10-18T02:40:00Z" -> "18/10/2026" */
private fun shortDate(iso: String?): String? {
    val d = iso?.take(10)?.split("-") ?: return null
    return if (d.size == 3) "${d[2]}/${d[1]}/${d[0]}" else null
}

/**
 * Riquadro del valore: mostra il risultato e, sotto, come è stato calcolato
 * (Cardmarket corretto per lingua + offerte CardTrader nella lingua della copia).
 */
@Composable
private fun ValueBox(
    card: CardDetail?,
    variant: Variant,
    condition: Condition,
    factors: ValueFactors,
    lang: String,
    langState: LangPriceState,
    langPrice: CardLangPrice?,
    quantity: Int,
    loading: Boolean,
) {
    val base = Valuation.baseValue(card, variant)
    val est = langPrice?.estimates?.get(variant)
    val coef = langPrice?.coef
    val langLabel = languageOf(lang).label
    val breakdown = PriceEngine.combine(
        cm = base?.amount,
        cmCurrency = base?.currency,
        ct = est?.nmAmount,
        ctCurrency = est?.currency,
        ctOffers = est?.offers ?: 0,
        langCoef = coef?.ratio ?: 1.0,
        langCoefLearned = coef != null,
    )
    val condFactor = factors.of(condition)
    val muted = OnAmber.copy(alpha = 0.85f)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AmberGradient)
            .padding(18.dp)
            .animateContentSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "VALORE STIMATO · ${langLabel.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnAmber.copy(alpha = 0.75f),
            )
            when {
                loading -> Text("Caricamento prezzi…", style = MaterialTheme.typography.bodyLarge, color = OnAmber)
                card == null -> Text("Nessun dato disponibile", style = MaterialTheme.typography.bodyLarge, color = OnAmber)
                breakdown == null -> Text(
                    "Prezzo non disponibile per questa carta o variante. Controlla i venduti su eBay qui sotto.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnAmber,
                )
                else -> {
                    val unit = breakdown.nmAmount * condFactor
                    Text(
                        money(unit, breakdown.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnAmber,
                    )
                    if (quantity > 1) {
                        Text(
                            "${money(unit * quantity, breakdown.currency)} per $quantity copie",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnAmber,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Come è calcolato", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnAmber)

                    // Cardmarket corretto per lingua
                    if (base != null && breakdown.cmAdjusted != null) {
                        val coefPct = (breakdown.langCoef * 100).roundToInt()
                        val coefWhy = when {
                            coef != null && langPrice?.coefFromSet == true ->
                                "calcolato su ${coef.samples} carte $langLabel di questo set"
                            coef != null -> "media $langLabel su ${coef.samples} carte"
                            else -> "nessuna correzione disponibile"
                        }
                        Text(
                            "• ${base.source} (tutte le lingue): ${money(base.amount, base.currency)} × $coefPct% " +
                                "($coefWhy) = ${money(breakdown.cmAdjusted, base.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }

                    // Offerte nella lingua
                    val offersLine = when (langState) {
                        LangPriceState.LOADING -> "• Offerte in $langLabel: caricamento…"
                        LangPriceState.UNSUPPORTED ->
                            "• Prezzi specifici per $langLabel non ancora disponibili (lingue coperte: EN, IT, FR, DE, ES, PT)"
                        LangPriceState.UNAVAILABLE ->
                            "• Prezzi per lingua non raggiungibili ora: controlla la connessione"
                        LangPriceState.OK -> if (est == null) {
                            "• Nessuna offerta in $langLabel per questa variante su CardTrader"
                        } else {
                            val date = shortDate(langPrice?.updated)?.let { " (dati del $it)" }.orEmpty()
                            "• Offerte in $langLabel su CardTrader: ${est.offers}, stima Near Mint " +
                                "${money(est.nmAmount, est.currency)}$date"
                        }
                    }
                    Text(offersLine, style = MaterialTheme.typography.bodySmall, color = muted)

                    // Fusione e condizione
                    val weightPct = (breakdown.ctWeight * 100).roundToInt()
                    if (weightPct in 1..99) {
                        Text(
                            "• Peso: offerte $weightPct%, Cardmarket ${100 - weightPct}% " +
                                "(più offerte ci sono, più contano)",
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                    Text(
                        "• Near Mint ${money(breakdown.nmAmount, breakdown.currency)} × ${(condFactor * 100).roundToInt()}% " +
                            "per ${condition.labelIt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                    if (base?.approximate == true && breakdown.ctWeight < 1.0) {
                        Text(
                            "Attenzione: Cardmarket non ha un prezzo per questa variante, è usato quello della versione base.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnAmber,
                        )
                    }
                }
            }
        }
    }
}
