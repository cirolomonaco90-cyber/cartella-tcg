package it.cartella.tcg.data

import android.content.Context
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
import kotlinx.coroutines.flow.Flow

/**
 * Una riga = una "copia" posseduta: stessa carta + lingua + condizione + variante.
 * [baseAmount] è il prezzo di riferimento salvato; il valore finale si calcola al volo
 * applicando il coefficiente della condizione, così cambiare le percentuali aggiorna
 * subito la collezione senza rete.
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
)

val OwnedCard.condition: Condition
    get() = Condition.entries.firstOrNull { it.name == conditionName } ?: Condition.NEAR_MINT

val OwnedCard.variant: Variant
    get() = Variant.entries.firstOrNull { it.name == variantName } ?: Variant.NORMAL

/** Coefficienti scelti dall'utente: per condizione e (correzione) per lingua. */
data class ValueFactors(
    val condition: Map<Condition, Double> = Condition.entries.associateWith { it.defaultFactor },
    val language: Map<String, Double> = emptyMap(),
) {
    fun of(c: Condition): Double = condition[c] ?: c.defaultFactor
    fun ofLang(lang: String): Double = language[lang] ?: 1.0
}

/** Valore di una singola copia: prezzo di riferimento × condizione × correzione lingua. */
fun OwnedCard.unitValue(f: ValueFactors): Double? =
    baseAmount?.let { it * f.of(condition) * f.ofLang(lang) }

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

@Database(entities = [OwnedCard::class], version = 1, exportSchema = false)
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
            ).build().also { instance = it }
        }
    }
}
