package it.cartella.tcg.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.data.OwnedCard
import it.cartella.tcg.data.ValueFactors
import it.cartella.tcg.data.condition
import it.cartella.tcg.data.languageOf
import it.cartella.tcg.data.unitValue
import it.cartella.tcg.data.valuation
import it.cartella.tcg.data.variant

private val SORT_LABELS = listOf("Recenti", "Valore", "Nome")

@Composable
fun CollectionScreen(vm: AppViewModel, onOpen: (OwnedCard) -> Unit, onGoSearch: () -> Unit) {
    val owned by vm.owned.collectAsState()
    val factors by vm.factors.collectAsState()
    val refresh by vm.refresh.collectAsState()

    var langFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var nameFilter by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }

    val values = owned.mapNotNull { row -> row.valuation(factors)?.let { row to it } }
    val totals = values
        .groupBy { it.second.currency }
        .mapValues { (_, rows) -> rows.sumOf { (row, v) -> v.unit * row.quantity } }
    val unpriced = owned.size - values.size
    val copies = owned.sumOf { it.quantity }
    val languages = owned.map { it.lang }.distinct()

    val visible = owned
        .filter {
            (langFilter == null || it.lang == langFilter) &&
                it.name.contains(nameFilter.trim(), ignoreCase = true)
        }
        .let { list ->
            when (sort) {
                1 -> list.sortedByDescending { (it.unitValue(factors) ?: -1.0) * it.quantity }
                2 -> list.sortedBy { it.name.lowercase() }
                else -> list
            }
        }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------------------ testata con il totale
        GradientHeader {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "La mia collezione",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "Valore stimato",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                if (owned.isNotEmpty()) {
                    if (refresh.running) {
                        Text(
                            "${refresh.done}/${refresh.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        CircularProgressIndicator(
                            Modifier.padding(horizontal = 12.dp).size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = vm::refreshPrices) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna prezzi", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            if (totals.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            } else {
                totals.forEach { (cur, sum) ->
                    Text(
                        money(sum, cur),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }
            if (unpriced > 0) {
                Text(
                    "$unpriced senza prezzo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("$copies", "carte")
                Stat("${owned.size}", "copie distinte")
                Stat("${languages.size}", if (languages.size == 1) "lingua" else "lingue")
            }
        }

        if (owned.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Star,
                title = "La tua collezione è vuota",
                text = "Cerca una carta, scegli lingua e condizione e aggiungila: qui vedrai il valore totale.",
                modifier = Modifier.weight(1f),
            ) {
                Button(onClick = onGoSearch, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cerca carte")
                }
            }
        } else {
            // -------------------------------------------------------- filtri e ordinamento
            OutlinedTextField(
                value = nameFilter,
                onValueChange = { nameFilter = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Cerca nella collezione") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SORT_LABELS.forEachIndexed { i, label ->
                    FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(label) })
                }
                if (languages.size > 1) {
                    languages.forEach { code ->
                        FilterChip(
                            selected = langFilter == code,
                            onClick = { langFilter = if (langFilter == code) null else code },
                            label = { Text(languageOf(code).display) },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { card ->
                    OwnedRow(card, factors, onOpen)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OwnedRow(
    card: OwnedCard,
    factors: ValueFactors,
    onOpen: (OwnedCard) -> Unit,
) {
    ElevatedCard(onClick = { onOpen(card) }) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardImage(card.imageBase, card.name, high = false, modifier = Modifier.width(60.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${card.setName} · #${card.localId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag(languageOf(card.lang).flag + " " + card.lang.uppercase())
                    Tag(card.condition.code)
                    Tag(card.variant.labelIt)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val v = card.valuation(factors)
                val unit = v?.unit
                val cur = v?.currency
                Text(
                    if (unit != null && cur != null) money(unit * card.quantity, cur) else "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "×${card.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Da dove viene il prezzo: offerte reali nella lingua o stima corretta.
                if (v != null) {
                    Text(
                        if (v.breakdown.ctWeight > 0) "${card.ctCount} offerte ${card.lang.uppercase()}" else "stima",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
