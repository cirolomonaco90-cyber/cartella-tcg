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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.data.Condition
import it.cartella.tcg.data.Pricing
import it.cartella.tcg.data.Valuation
import it.cartella.tcg.data.Variant
import it.cartella.tcg.data.condition
import it.cartella.tcg.data.variant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val d by vm.detail.collectAsState()
    val factors by vm.factors.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        Text(
                            "${card.setName} · #${card.localId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            card.rarity?.let { Tag(it, Color.White.copy(alpha = 0.22f), Color.White) }
                            card.illustrator?.let { Tag(it, Color.White.copy(alpha = 0.22f), Color.White) }
                        }
                    }
                }
            }

            d.error?.let { ErrorBanner(it) }

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
                pricing = card?.pricing,
                variant = variant,
                condition = condition,
                factor = factors[condition] ?: condition.defaultFactor,
                quantity = quantity,
                cardLoaded = card != null,
            )

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

@Composable
private fun ValueBox(
    pricing: Pricing?,
    variant: Variant,
    condition: Condition,
    factor: Double,
    quantity: Int,
    cardLoaded: Boolean,
) {
    val base = Valuation.baseValue(pricing, variant)
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
                "VALORE STIMATO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnAmber.copy(alpha = 0.75f),
            )
            when {
                !cardLoaded -> Text(
                    "Caricamento prezzi…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnAmber,
                )

                base == null -> Text(
                    "Prezzo non disponibile per questa carta/variante",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnAmber,
                )

                else -> {
                    Text(
                        money(base.amount * factor, base.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnAmber,
                    )
                    if (quantity > 1) {
                        Text(
                            "${money(base.amount * factor * quantity, base.currency)} per $quantity copie",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnAmber,
                        )
                    }
                    val approx = if (base.approximate) " · stima approssimata per questa variante" else ""
                    Text(
                        "Riferimento ${money(base.amount, base.currency)} (${base.source}) " +
                            "× ${(factor * 100).roundToInt()}% per ${condition.labelIt}$approx",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmber.copy(alpha = 0.85f),
                    )
                }
            }
            Text(
                "Stima dai prezzi medi di mercato, non dai singoli venduti.",
                style = MaterialTheme.typography.labelSmall,
                color = OnAmber.copy(alpha = 0.75f),
            )
        }
    }
}
