package it.cartella.tcg.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Una riga = una "copia" posseduta: stessa carta + lingua + condizione + variante.
 *
 * Salviamo i dati grezzi delle due fonti e calcoliamo il valore al volo con [valuation]:
 * - [baseAmount]/[currency]: prezzo Cardmarket (o TCGplayer) Near Mint, uguale per tutte le lingue;
 * - [ctAmount]/[ctCurrency]/[ctCount]: stima Near Mint dalle offerte CardTrader nella lingua della copia.
 * Così cambiare le percentuali o i coefficienti aggiorna subito la collezione, senza rete.
 */
@Entity(tableName = "owned_cards")
data class OwnedCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val lang: String,
    val name: String,
    val setName: String,
    val localId: String,
    val rarity: String?,
    val imageBase: String?,
    val conditionName: String,
    val variantName: String,
    val quantity: Int,
    val baseAmount: Double?,
    val currency: String?,
    val priceSource: String?,
    val priceUpdated: Long?,
    val addedAt: Long,
    // --- dalla versione 2 del database: prezzi per lingua da CardTrader
    val ctAmount: Double? = null,
    val ctCurrency: String? = null,
    @ColumnInfo(defaultValue = "0") val ctCount: Int = 0,
    val ctBlueprint: Int? = null,
    val ctUpdated: Long? = null,
)

val OwnedCard.condition: Condition
    get() = Condition.entries.firstOrNull { it.name == conditionName } ?: Condition.NEAR_MINT

val OwnedCard.variant: Variant
    get() = Variant.entries.firstOrNull { it.name == variantName } ?: Variant.NORMAL

/**
 * Coefficienti per il calcolo del valore:
 * - per condizione (scelti dall'utente);
 * - per lingua: imparati dalla collezione ([learned]) se ci sono abbastanza carte,
 *   altrimenti quelli impostati a mano ([language], default 100%).
 */
data class ValueFactors(
    val condition: Map<Condition, Double> = Condition.entries.associateWith { it.defaultFactor },
    val language: Map<String, Double> = emptyMap(),
    val learned: Map<String, LearnedCoef> = emptyMap(),
) {
    fun of(c: Condition): Double = condition[c] ?: c.defaultFactor
    fun manualLang(lang: String): Double = language[lang] ?: 1.0
    fun learnedLang(lang: String): LearnedCoef? =
        learned[lang]?.takeIf { it.samples >= PriceEngine.MIN_LEARN }
    fun ofLang(lang: String): Double = learnedLang(lang)?.ratio ?: manualLang(lang)
}

data class RowValue(val unit: Double, val currency: String, val breakdown: PriceBreakdown)

/** Valore di una singola copia: prezzo di riferimento per la sua lingua × condizione. */
fun OwnedCard.valuation(f: ValueFactors): RowValue? {
    val b = PriceEngine.combine(
        cm = baseAmount,
        cmCurrency = currency,
        ct = ctAmount,
        ctCurrency = ctCurrency,
        ctOffers = ctCount,
        langCoef = f.ofLang(lang),
        langCoefLearned = f.learnedLang(lang) != null,
    ) ?: return null
    return RowValue(b.nmAmount * f.of(condition), b.currency, b)
}

fun OwnedCard.unitValue(f: ValueFactors): Double? = valuation(f)?.unit

/** Campioni per imparare il coefficiente di lingua (solo dati affidabili, in euro). */
fun List<OwnedCard>.learningSamples(): List<PriceEngine.LearningSample> = mapNotNull { r ->
    val cm = r.baseAmount ?: return@mapNotNull null
    val ct = r.ctAmount ?: return@mapNotNull null
    if (r.ctCount < PriceEngine.MIN_OFFERS_FOR_LEARNING) return@mapNotNull null
    if (r.currency != "EUR" || r.ctCurrency != "EUR" || r.priceSource != "Cardmarket") return@mapNotNull null
    PriceEngine.LearningSample(r.lang, "${r.cardId}|${r.variantName}", cm, ct)
}

@Dao
interface OwnedCardDao {
    @Query("SELECT * FROM owned_cards ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<OwnedCard>>

    @Query(
        "SELECT * FROM owned_cards WHERE cardId = :cardId AND lang = :lang " +
            "AND conditionName = :conditionName AND variantName = :variantName LIMIT 1",
    )
    suspend fun find(cardId: String, lang: String, conditionName: String, variantName: String): OwnedCard?

    @Query(
        "SELECT * FROM owned_cards WHERE cardId = :cardId AND lang = :lang " +
            "AND conditionName = :conditionName AND variantName = :variantName AND id != :excludeId LIMIT 1",
    )
    suspend fun findOther(
        cardId: String,
        lang: String,
        conditionName: String,
        variantName: String,
        excludeId: Long,
    ): OwnedCard?

    @Insert
    suspend fun insert(card: OwnedCard): Long

    @Update
    suspend fun update(card: OwnedCard)

    @Delete
    suspend fun delete(card: OwnedCard)
}

/** v1 -> v2: colonne per i prezzi CardTrader. La collezione esistente resta intatta. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE owned_cards ADD COLUMN ctAmount REAL")
        db.execSQL("ALTER TABLE owned_cards ADD COLUMN ctCurrency TEXT")
        db.execSQL("ALTER TABLE owned_cards ADD COLUMN ctCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE owned_cards ADD COLUMN ctBlueprint INTEGER")
        db.execSQL("ALTER TABLE owned_cards ADD COLUMN ctUpdated INTEGER")
    }
}

@Database(entities = [OwnedCard::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): OwnedCardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "cartella.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
