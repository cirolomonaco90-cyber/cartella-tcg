package it.cartella.tcg

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.cartella.tcg.data.ApiException
import it.cartella.tcg.data.AppDatabase
import it.cartella.tcg.data.CardBrief
import it.cartella.tcg.data.CardDetail
import it.cartella.tcg.data.Condition
import it.cartella.tcg.data.NotFoundException
import it.cartella.tcg.data.OwnedCard
import it.cartella.tcg.data.SetBrief
import it.cartella.tcg.data.SettingsStore
import it.cartella.tcg.data.TcgdexApi
import it.cartella.tcg.data.Valuation
import it.cartella.tcg.data.ValueFactors
import it.cartella.tcg.data.Variant
import it.cartella.tcg.data.variant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchMode { NAME, SET }

data class SearchUiState(
    val mode: SearchMode = SearchMode.NAME,
    val query: String = "",
    val searched: Boolean = false,
    val results: List<CardBrief> = emptyList(),
    val page: Int = 0,
    val endReached: Boolean = false,
    val sets: List<SetBrief> = emptyList(),
    val setFilter: String = "",
    val selectedSet: SetBrief? = null,
    val setCards: List<CardBrief> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class DetailUiState(
    val open: Boolean = false,
    val brief: CardBrief? = null,
    val lang: String = "it",
    val card: CardDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Non nullo quando si modifica una copia già in collezione. */
    val editing: OwnedCard? = null,
)

data class RefreshState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
)

/** Messaggio per la snackbar, con un'eventuale azione (es. "Annulla"). */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val dao = AppDatabase.get(app).dao()

    private val _language = MutableStateFlow("it")
    val language: StateFlow<String> = _language.asStateFlow()

    val factors: StateFlow<ValueFactors> =
        settings.factors.stateIn(viewModelScope, SharingStarted.Eagerly, ValueFactors())

    val owned: StateFlow<List<OwnedCard>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Quante copie possiedi di ogni carta (tutte le lingue), per i badge e l'avanzamento dei set. */
    val ownedCounts: StateFlow<Map<String, Int>> = owned
        .map { list -> list.groupBy { it.cardId }.mapValues { (_, rows) -> rows.sumOf { r -> r.quantity } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _refresh = MutableStateFlow(RefreshState())
    val refresh: StateFlow<RefreshState> = _refresh.asStateFlow()

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            settings.language.collect { _language.value = it }
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun <T> api(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.userMessage(): String =
        (this as? ApiException)?.message ?: "Errore imprevisto"

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, action))
    }

    // ---------------------------------------------------------------- lingua

    fun setLanguage(code: String) {
        if (code == _language.value) return
        _language.value = code
        viewModelScope.launch { settings.setLanguage(code) }
        // I risultati sono in una lingua precisa: ripetiamo la ricerca nella nuova.
        val s = _search.value
        searchJob?.cancel()
        _search.update {
            it.copy(sets = emptyList(), setCards = emptyList(), selectedSet = null, loading = false)
        }
        when {
            s.mode == SearchMode.NAME && s.searched -> startNameSearch()
            s.mode == SearchMode.SET -> loadSets()
        }
    }

    // ---------------------------------------------------------------- ricerca

    fun onQueryChange(q: String) = _search.update { it.copy(query = q) }

    fun onSetFilterChange(q: String) = _search.update { it.copy(setFilter = q) }

    fun setMode(mode: SearchMode) {
        if (mode == _search.value.mode) return
        // Ogni modalità ha il suo caricamento: annulliamo quello dell'altra.
        searchJob?.cancel()
        _search.update { it.copy(mode = mode, error = null, loading = false) }
        if (mode == SearchMode.SET && _search.value.sets.isEmpty()) loadSets()
    }

    fun submitSearch() = startNameSearch()

    fun quickSearch(name: String) {
        _search.update { it.copy(query = name) }
        startNameSearch()
    }

    private fun startNameSearch() {
        val q = _search.value.query.trim()
        searchJob?.cancel()
        if (q.length < 2) {
            _search.update { it.copy(error = "Scrivi almeno 2 caratteri", loading = false) }
            return
        }
        _search.update {
            it.copy(results = emptyList(), page = 0, endReached = false, searched = true, error = null)
        }
        loadPage(q)
    }

    fun loadMore() {
        val s = _search.value
        if (s.loading || s.endReached || !s.searched || s.mode != SearchMode.NAME || s.error != null) return
        loadPage(s.query.trim())
    }

    private fun loadPage(q: String) {
        val lang = _language.value
        val nextPage = _search.value.page + 1
        _search.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val res = api { TcgdexApi.searchCardsWithFallback(lang, q, nextPage, PAGE_SIZE) }
            val result = res.getOrNull()
            if (result != null) {
                _search.update { s ->
                    s.copy(
                        results = (s.results + result.cards).distinctBy { it.id },
                        page = nextPage,
                        endReached = result.endReached,
                        loading = false,
                    )
                }
            } else {
                _search.update {
                    it.copy(loading = false, error = res.exceptionOrNull()?.userMessage())
                }
            }
        }
    }

    fun retry() {
        val s = _search.value
        when {
            s.mode == SearchMode.NAME -> if (s.results.isEmpty()) startNameSearch() else {
                _search.update { it.copy(error = null) }
                loadMore()
            }
            s.selectedSet != null -> openSet(s.selectedSet)
            else -> loadSets()
        }
    }

    private fun loadSets() {
        val lang = _language.value
        searchJob?.cancel()
        _search.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val res = api { TcgdexApi.setsWithFallback(lang) }
            val sets = res.getOrNull()
            if (sets != null) {
                // TCGdex elenca i set dal più vecchio: mostriamo prima i più recenti.
                _search.update { it.copy(sets = sets.reversed(), loading = false) }
            } else {
                _search.update { it.copy(loading = false, error = res.exceptionOrNull()?.userMessage()) }
            }
        }
    }

    fun openSet(set: SetBrief) {
        val lang = _language.value
        searchJob?.cancel()
        _search.update { it.copy(selectedSet = set, setCards = emptyList(), loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val res = api { TcgdexApi.setCardsWithFallback(lang, set.id) }
            val cards = res.getOrNull()
            if (cards != null) {
                _search.update { it.copy(setCards = cards, loading = false) }
            } else {
                _search.update { it.copy(loading = false, error = res.exceptionOrNull()?.userMessage()) }
            }
        }
    }

    fun closeSet() {
        searchJob?.cancel()
        _search.update { it.copy(selectedSet = null, setCards = emptyList(), loading = false, error = null) }
    }

    // ---------------------------------------------------------------- dettaglio carta

    fun openCard(brief: CardBrief) {
        _detail.value = DetailUiState(open = true, brief = brief, lang = _language.value, loading = true)
        loadDetail()
    }

    fun openOwned(o: OwnedCard) {
        _detail.value = DetailUiState(
            open = true,
            brief = CardBrief(o.cardId, o.localId, o.name, o.imageBase),
            lang = o.lang,
            loading = true,
            editing = o,
        )
        loadDetail()
    }

    /** Cambia la lingua della copia: l'id carta è lo stesso, cambiano testi e immagine. */
    fun setDetailLanguage(code: String) {
        if (code == _detail.value.lang) return
        _detail.update { it.copy(lang = code, card = null, loading = true, error = null) }
        loadDetail()
    }

    fun retryDetail() {
        _detail.update { it.copy(loading = true, error = null) }
        loadDetail()
    }

    private fun loadDetail() {
        detailJob?.cancel()
        val d = _detail.value
        val id = d.brief?.id ?: return
        detailJob = viewModelScope.launch {
            val res = api { TcgdexApi.cardWithFallback(d.lang, id) }
            val card = res.getOrNull()
            if (card != null) {
                _detail.update { it.copy(card = card, loading = false, error = null) }
            } else {
                val failure = res.exceptionOrNull()
                val msg = if (failure is NotFoundException) {
                    "Carta non trovata su TCGdex"
                } else {
                    failure?.userMessage()
                }
                _detail.update { it.copy(card = null, loading = false, error = msg) }
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _detail.value = DetailUiState()
    }

    // ---------------------------------------------------------------- collezione

    fun addToCollection(
        card: CardDetail,
        lang: String,
        condition: Condition,
        variant: Variant,
        quantity: Int,
    ) {
        val base = Valuation.baseValue(card, variant)
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val existing = dao.find(card.id, lang, condition.name, variant.name)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        quantity = existing.quantity + quantity,
                        baseAmount = base?.amount ?: existing.baseAmount,
                        currency = base?.currency ?: existing.currency,
                        priceSource = base?.source ?: existing.priceSource,
                        priceUpdated = if (base != null) now else existing.priceUpdated,
                    ),
                )
            } else {
                dao.insert(
                    OwnedCard(
                        cardId = card.id,
                        lang = lang,
                        name = card.name,
                        setName = card.setName,
                        localId = card.localId,
                        rarity = card.rarity,
                        imageBase = card.imageBase,
                        conditionName = condition.name,
                        variantName = variant.name,
                        quantity = quantity,
                        baseAmount = base?.amount,
                        currency = base?.currency,
                        priceSource = base?.source,
                        priceUpdated = if (base != null) now else null,
                        addedAt = now,
                    ),
                )
            }
            say("${card.name} aggiunta alla collezione")
        }
    }

    fun updateOwned(
        old: OwnedCard,
        card: CardDetail?,
        lang: String,
        condition: Condition,
        variant: Variant,
        quantity: Int,
    ) {
        val base = card?.let { Valuation.baseValue(it, variant) }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            var updated = old.copy(
                lang = lang,
                conditionName = condition.name,
                variantName = variant.name,
                quantity = quantity,
            )
            if (card != null) {
                updated = updated.copy(
                    name = card.name,
                    setName = card.setName,
                    localId = card.localId,
                    rarity = card.rarity,
                    imageBase = card.imageBase,
                )
            }
            if (base != null) {
                updated = updated.copy(
                    baseAmount = base.amount,
                    currency = base.currency,
                    priceSource = base.source,
                    priceUpdated = now,
                )
            }
            // Se ora coincide con un'altra copia (stessa lingua, condizione e variante) le uniamo.
            val twin = dao.findOther(updated.cardId, lang, condition.name, variant.name, old.id)
            if (twin != null) {
                dao.update(updated.copy(id = twin.id, quantity = twin.quantity + quantity, addedAt = twin.addedAt))
                dao.delete(old)
                say("Unita con una copia identica già in collezione")
            } else {
                dao.update(updated)
                say("Modifiche salvate")
            }
        }
    }

    fun deleteOwned(o: OwnedCard) {
        viewModelScope.launch {
            dao.delete(o)
            say("${o.name} rimossa dalla collezione", "Annulla") {
                viewModelScope.launch { dao.insert(o.copy(id = 0)) }
            }
        }
    }

    /** Riscarica i prezzi di tutte le carte in collezione (una richiesta per carta+lingua). */
    fun refreshPrices() {
        if (_refresh.value.running) return
        val groups = owned.value.groupBy { it.cardId to it.lang }
        if (groups.isEmpty()) return
        _refresh.value = RefreshState(running = true, done = 0, total = groups.size)
        viewModelScope.launch {
            var failed = 0
            var done = 0
            for ((key, rows) in groups) {
                val card = api { TcgdexApi.cardWithFallback(key.second, key.first) }.getOrNull()
                if (card == null) {
                    failed++
                } else {
                    val now = System.currentTimeMillis()
                    for (row in rows) {
                        val base = Valuation.baseValue(card, row.variant) ?: continue
                        // Rilegge la riga: l'utente potrebbe averla modificata nel frattempo.
                        val current = dao.find(row.cardId, row.lang, row.conditionName, row.variantName) ?: continue
                        dao.update(
                            current.copy(
                                baseAmount = base.amount,
                                currency = base.currency,
                                priceSource = base.source,
                                priceUpdated = now,
                            ),
                        )
                    }
                }
                done++
                _refresh.update { it.copy(done = done) }
                delay(120)
            }
            _refresh.value = RefreshState()
            say(if (failed == 0) "Prezzi aggiornati" else "Prezzi aggiornati, $failed carte non raggiungibili")
        }
    }

    // ---------------------------------------------------------------- impostazioni

    fun setFactor(c: Condition, value: Double) {
        viewModelScope.launch { settings.setFactor(c, value) }
    }

    fun setLanguageFactor(code: String, value: Double) {
        viewModelScope.launch { settings.setLanguageFactor(code, value) }
    }

    fun resetFactors() {
        viewModelScope.launch { settings.resetFactors() }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
