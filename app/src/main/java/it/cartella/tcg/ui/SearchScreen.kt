package it.cartella.tcg.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.cartella.tcg.AppViewModel
import it.cartella.tcg.SearchMode
import it.cartella.tcg.data.CardBrief
import it.cartella.tcg.data.assetUrl

private val SUGGESTIONS = listOf("Pikachu", "Charizard", "Mewtwo", "Eevee", "Gengar", "Lugia")

@Composable
fun SearchScreen(vm: AppViewModel, onOpenCard: (CardBrief) -> Unit) {
    val s by vm.search.collectAsState()
    val lang by vm.language.collectAsState()
    val counts by vm.ownedCounts.collectAsState()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Text(
                "Cerca carte",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Ogni carta, in ogni lingua",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))

            if (s.mode == SearchMode.NAME) {
                HeaderSearchField(
                    value = s.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = "Nome della carta, es. Pikachu",
                    onSearch = {
                        focus.clearFocus()
                        vm.submitSearch()
                    },
                )
            } else if (s.selectedSet == null) {
                HeaderSearchField(
                    value = s.setFilter,
                    onValueChange = vm::onSetFilterChange,
                    placeholder = "Filtra i set per nome",
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanguageMenu(selected = lang, onSelect = vm::setLanguage)
                Pill("Per nome", selected = s.mode == SearchMode.NAME, onClick = { vm.setMode(SearchMode.NAME) })
                Pill("Per set", selected = s.mode == SearchMode.SET, onClick = { vm.setMode(SearchMode.SET) })
            }
        }

        s.error?.let { ErrorBanner(it, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }

        Box(Modifier.weight(1f)) {
            when {
                s.mode == SearchMode.NAME -> CardGrid(
                    cards = s.results,
                    counts = counts,
                    loading = s.loading,
                    onLoadMore = vm::loadMore,
                    onOpen = onOpenCard,
                    empty = {
                        if (s.searched && s.error == null) {
                            EmptyState(
                                icon = Icons.Default.Search,
                                title = "Nessun risultato",
                                text = "Prova un altro nome o cambia lingua: non tutte le carte esistono in ogni lingua.",
                            )
                        } else {
                            StartState(onPick = vm::quickSearch)
                        }
                    },
                )

                s.selectedSet == null -> SetList(vm)

                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::closeSet) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                        Text(
                            s.selectedSet?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        CardGrid(
                            cards = s.setCards,
                            counts = counts,
                            loading = s.loading,
                            onLoadMore = {},
                            onOpen = onOpenCard,
                            empty = {
                                EmptyState(
                                    icon = Icons.Default.Search,
                                    title = "Nessuna carta",
                                    text = "Questo set non ha carte nella lingua scelta.",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Schermata iniziale: invita a cercare e propone qualche ricerca rapida. */
@Composable
private fun StartState(onPick: (String) -> Unit) {
    val uri = LocalUriHandler.current
    EmptyState(
        icon = Icons.Default.Search,
        title = "Trova la tua prossima carta",
        text = "Scrivi un nome qui sopra, oppure prova una ricerca rapida.",
        modifier = Modifier.fillMaxSize(),
    ) {
        SUGGESTIONS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name ->
                    AssistChip(onClick = { onPick(name) }, label = { Text(name) })
                }
            }
        }
        TextButton(onClick = { uri.openUri("https://tcgdex.dev") }) {
            Text("Dati forniti da TCGdex · tcgdex.dev")
        }
    }
}

@Composable
private fun CardGrid(
    cards: List<CardBrief>,
    counts: Map<String, Int>,
    loading: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (CardBrief) -> Unit,
    empty: @Composable () -> Unit,
) {
    if (cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator() else empty()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(cards) { index, card ->
                if (index >= cards.size - 6) {
                    LaunchedEffect(cards.size) { onLoadMore() }
                }
                ElevatedCard(onClick = { onOpen(card) }) {
                    Box {
                        CardImage(
                            card.imageBase,
                            card.name,
                            high = false,
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                        )
                        val owned = counts[card.id]
                        if (owned != null) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    "×$owned",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                        Text(
                            card.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "#${card.localId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SetList(vm: AppViewModel) {
    val s by vm.search.collectAsState()
    val filtered = s.sets.filter { it.name.contains(s.setFilter.trim(), ignoreCase = true) }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (s.loading) {
                CircularProgressIndicator()
            } else {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Nessun set trovato",
                    text = "Controlla il nome o cambia lingua.",
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered) { set ->
                ElevatedCard(onClick = { vm.openSet(set) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.width(88.dp).height(52.dp),
                        ) {
                            Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = assetUrl(set.logoBase),
                                    contentDescription = set.name,
                                    modifier = Modifier.size(width = 76.dp, height = 40.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                set.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${set.total} carte",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
