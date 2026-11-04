package cr.monny.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * DAOs de Room. Cada interfaz agrupa las consultas de una tabla.
 *
 * Los métodos `observe...` devuelven Flow y se actualizan solos cuando cambia la tabla;
 * los métodos `suspend` son operaciones puntuales (insertar, actualizar, borrar).
 * Los métodos `all()`/`insertAll()`/`clear()` existen para los respaldos JSON.
 */

/** Acceso a las tarjetas de crédito. */
@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE archived = 0 ORDER BY id")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE archived = 0 ORDER BY id")
    suspend fun allOnce(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: Long): Card?

    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("SELECT * FROM cards")
    suspend fun all(): List<Card>

    @Insert
    suspend fun insertAll(cards: List<Card>)

    @Query("DELETE FROM cards")
    suspend fun clear()
}

/** Acceso a las categorías de gasto (con su presupuesto). */
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun expenseCount(categoryId: Long): Int

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

/** Acceso a los gastos (incluye cuotas de tasa 0 y cobros de gastos fijos). */
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE cardId = :cardId AND cycleKey = :cycleKey ORDER BY date DESC, id DESC")
    fun observeCycle(cardId: Long, cycleKey: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE cardId = :cardId ORDER BY date DESC, id DESC")
    fun observeAll(cardId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeEverything(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: Long): Expense?

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query(
        "SELECT merchant, COUNT(*) AS uses, MAX(date) AS lastUsed FROM expenses " +
            "WHERE merchant != '' GROUP BY merchant ORDER BY uses DESC, lastUsed DESC LIMIT 40",
    )
    fun observeMerchants(): Flow<List<MerchantHint>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE cardId = :cardId AND cycleKey = :cycleKey")
    suspend fun totalForCycle(cardId: Long, cycleKey: Long): Long

    @Query("DELETE FROM expenses WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("DELETE FROM expenses WHERE cardId = :cardId AND cycleKey = :cycleKey")
    suspend fun deleteCycle(cardId: Long, cycleKey: Long)

    @Query("DELETE FROM expenses WHERE planId = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("DELETE FROM expenses WHERE planId = :planId AND cycleKey >= :fromCycle")
    suspend fun deletePlanFrom(planId: Long, fromCycle: Long)

    @Query("SELECT cycleKey FROM expenses WHERE recurringId = :ruleId")
    suspend fun recurringCycles(ruleId: Long): List<Long>

    @Query("DELETE FROM expenses WHERE recurringId = :ruleId")
    suspend fun deleteRecurring(ruleId: Long)

    @Query("DELETE FROM expenses WHERE recurringId = :ruleId AND cycleKey >= :fromCycle")
    suspend fun deleteRecurringFrom(ruleId: Long, fromCycle: Long)

    @Query("SELECT * FROM expenses")
    suspend fun all(): List<Expense>

    @Insert
    suspend fun insertAll(expenses: List<Expense>)

    @Query("DELETE FROM expenses")
    suspend fun clear()
}

/** Resultado de la consulta de comercios frecuentes (para autocompletar). */
data class MerchantHint(
    val merchant: String,
    val uses: Int,
    val lastUsed: Long,
)

/** Acceso a los pagos (abonos) de la tarjeta. */
@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE cardId = :cardId AND cycleKey = :cycleKey ORDER BY date DESC, id DESC")
    fun observeCycle(cardId: Long, cycleKey: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC")
    fun observeEverything(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE cardId = :cardId ORDER BY date DESC, id DESC")
    fun observeForCard(cardId: Long): Flow<List<Payment>>

    @Query("DELETE FROM payments WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("DELETE FROM payments WHERE cardId = :cardId AND cycleKey = :cycleKey")
    suspend fun deleteCycle(cardId: Long, cycleKey: Long)

    @Insert
    suspend fun insert(payment: Payment): Long

    @Update
    suspend fun update(payment: Payment)

    @Delete
    suspend fun delete(payment: Payment)

    @Query("SELECT * FROM payments")
    suspend fun all(): List<Payment>

    @Insert
    suspend fun insertAll(payments: List<Payment>)

    @Query("DELETE FROM payments")
    suspend fun clear()
}

/** Acceso a los apartados ("en sobre") de cada ciclo. */
@Dao
interface CoverDao {
    @Query("SELECT * FROM covers WHERE cardId = :cardId AND cycleKey = :cycleKey ORDER BY date DESC, id DESC")
    fun observeCycle(cardId: Long, cycleKey: Long): Flow<List<Cover>>

    @Query("SELECT * FROM covers ORDER BY date DESC, id DESC")
    fun observeEverything(): Flow<List<Cover>>

    @Query("SELECT * FROM covers WHERE cardId = :cardId ORDER BY date DESC, id DESC")
    fun observeForCard(cardId: Long): Flow<List<Cover>>

    @Query("DELETE FROM covers WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("SELECT * FROM covers WHERE cardId = :cardId AND cycleKey = :cycleKey")
    suspend fun byCycle(cardId: Long, cycleKey: Long): List<Cover>

    @Query("DELETE FROM covers WHERE cardId = :cardId AND cycleKey = :cycleKey")
    suspend fun deleteCycle(cardId: Long, cycleKey: Long)

    @Insert
    suspend fun insert(cover: Cover): Long

    @Delete
    suspend fun delete(cover: Cover)

    @Query("SELECT * FROM covers")
    suspend fun all(): List<Cover>

    @Insert
    suspend fun insertAll(covers: List<Cover>)

    @Query("DELETE FROM covers")
    suspend fun clear()
}

/** Acceso a los sobres de ahorro y sus movimientos. */
@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelopes ORDER BY createdAt, id")
    fun observeAll(): Flow<List<Envelope>>

    @Query("SELECT * FROM envelopes WHERE id = :id")
    suspend fun byId(id: Long): Envelope?

    @Insert
    suspend fun insert(envelope: Envelope): Long

    @Update
    suspend fun update(envelope: Envelope)

    @Delete
    suspend fun delete(envelope: Envelope)

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM envelope_txs WHERE envelopeId = :envelopeId")
    fun observeBalance(envelopeId: Long): Flow<Long>

    @Query("SELECT envelopeId, COALESCE(SUM(amountCents), 0) AS balance FROM envelope_txs GROUP BY envelopeId")
    fun observeBalances(): Flow<List<EnvelopeBalance>>

    @Query("SELECT * FROM envelope_txs WHERE envelopeId = :envelopeId ORDER BY date DESC, id DESC")
    fun observeTxs(envelopeId: Long): Flow<List<EnvelopeTx>>

    @Insert
    suspend fun insertTx(tx: EnvelopeTx): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM envelope_txs WHERE envelopeId = :envelopeId AND date <= :until")
    suspend fun balanceUntil(envelopeId: Long, until: Long): Long

    @Query("SELECT COUNT(*) FROM envelope_txs WHERE envelopeId = :envelopeId")
    suspend fun txCount(envelopeId: Long): Int

    @Query("SELECT * FROM envelopes")
    suspend fun all(): List<Envelope>

    @Insert
    suspend fun insertAll(envelopes: List<Envelope>)

    @Query("DELETE FROM envelopes")
    suspend fun clear()

    @Query("SELECT * FROM envelope_txs")
    suspend fun allTxs(): List<EnvelopeTx>

    @Insert
    suspend fun insertAllTxs(txs: List<EnvelopeTx>)

    @Query("DELETE FROM envelope_txs")
    suspend fun clearTxs()
}

/** Saldo calculado de un sobre (suma de sus movimientos). */
data class EnvelopeBalance(
    val envelopeId: Long,
    val balance: Long,
)

/** Acceso a las compras a tasa 0. */
@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InstallmentPlan>>

    @Query("SELECT * FROM plans")
    suspend fun all(): List<InstallmentPlan>

    @Insert
    suspend fun insertAll(plans: List<InstallmentPlan>)

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): InstallmentPlan?

    @Insert
    suspend fun insert(plan: InstallmentPlan): Long

    @Update
    suspend fun update(plan: InstallmentPlan)

    @Delete
    suspend fun delete(plan: InstallmentPlan)

    @Query("DELETE FROM plans")
    suspend fun clear()

    @Query("DELETE FROM plans WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)
}

/**
 * Acceso a los gastos fijos y al registro de ciclos ya generados.
 * `recurring_charges` evita duplicados y que un cobro borrado se regenere.
 */
@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY active DESC, merchant")
    fun observeAll(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring ORDER BY active DESC, merchant")
    suspend fun all(): List<RecurringExpense>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun active(): List<RecurringExpense>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun byId(id: Long): RecurringExpense?

    @Insert
    suspend fun insert(rule: RecurringExpense): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringExpense>)

    @Update
    suspend fun update(rule: RecurringExpense)

    @Delete
    suspend fun delete(rule: RecurringExpense)

    @Query("DELETE FROM recurring WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("DELETE FROM recurring")
    suspend fun clear()

    @Query("SELECT * FROM recurring_charges")
    suspend fun allCharges(): List<RecurringCharge>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertCharge(charge: RecurringCharge)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAllCharges(charges: List<RecurringCharge>)

    @Query("SELECT cycleKey FROM recurring_charges WHERE ruleId = :ruleId")
    suspend fun chargeCycles(ruleId: Long): List<Long>

    @Query("DELETE FROM recurring_charges WHERE ruleId = :ruleId")
    suspend fun deleteCharges(ruleId: Long)

    @Query("DELETE FROM recurring_charges WHERE ruleId = :ruleId AND cycleKey >= :fromCycle")
    suspend fun deleteChargesFrom(ruleId: Long, fromCycle: Long)

    @Query("DELETE FROM recurring_charges")
    suspend fun clearCharges()
}
