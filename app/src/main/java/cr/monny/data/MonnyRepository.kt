package cr.monny.data

import androidx.room.withTransaction
import cr.monny.domain.CsvImport
import cr.monny.domain.CycleMath
import cr.monny.domain.ImportedExpense
import cr.monny.domain.ImportedPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/*
 * Repositorio único de la app: traduce acciones de la UI a operaciones de la base
 * de datos y expone los datos como Flow.
 *
 * Aquí vive la lógica que no es puramente visual: cálculo de ciclos al guardar,
 * apartados/retiros, cuotas, gastos fijos, importación y respaldos.
 */

/** Datos de un ciclo: gastos, pagos y apartados. */
data class CycleData(
    val expenses: List<Expense> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val covers: List<Cover> = emptyList(),
)

/** Resultado de una importación: cuántos gastos/pagos y si se aplicó el "en sobre". */
data class ImportSummary(
    val expenses: Int = 0,
    val payments: Int = 0,
    val coverApplied: Boolean = false,
)

/**
 * Repositorio principal. Mantiene la coherencia entre tarjetas, gastos, ciclos,
 * sobres, cuotas y gastos fijos.
 */
class MonnyRepository(private val db: MonnyDb) {

    private val recurringMutex = Mutex()

    val cards: Flow<List<Card>> = db.cards().observeAll()
    val categories: Flow<List<Category>> = db.categories().observeAll()
    val envelopes: Flow<List<Envelope>> = db.envelopes().observeAll()
    val envelopeBalances: Flow<List<EnvelopeBalance>> = db.envelopes().observeBalances()
    val merchantHints: Flow<List<MerchantHint>> = db.expenses().observeMerchants()
    val plans: Flow<List<InstallmentPlan>> = db.plans().observeAll()
    val recurring: Flow<List<RecurringExpense>> = db.recurring().observeAll()

    fun observeCycle(cardId: Long, cycleKey: Long): Flow<CycleData> = combine(
        db.expenses().observeCycle(cardId, cycleKey),
        db.payments().observeCycle(cardId, cycleKey),
        db.covers().observeCycle(cardId, cycleKey),
    ) { expenses, payments, covers ->
        CycleData(expenses, payments, covers)
    }

    fun observeAllExpenses(cardId: Long): Flow<List<Expense>> = db.expenses().observeAll(cardId)

    fun observeEverything(): Flow<List<Expense>> = db.expenses().observeEverything()

    fun observePaymentsForCard(cardId: Long): Flow<List<Payment>> = db.payments().observeForCard(cardId)

    fun observeCoversForCard(cardId: Long): Flow<List<Cover>> = db.covers().observeForCard(cardId)

    fun observePayments(cardId: Long, cycleKey: Long): Flow<List<Payment>> =
        db.payments().observeCycle(cardId, cycleKey)

    fun observeEnvelopeTxs(envelopeId: Long): Flow<List<EnvelopeTx>> =
        db.envelopes().observeTxs(envelopeId)

    fun observeEnvelopeBalance(envelopeId: Long): Flow<Long> =
        db.envelopes().observeBalance(envelopeId)

    // ---- Tarjetas ----

    suspend fun card(id: Long): Card? = db.cards().byId(id)

    suspend fun saveCard(card: Card): Long {
        val existing = if (card.id != 0L) db.cards().byId(card.id) else null
        val id = if (card.id == 0L) {
            db.cards().insert(card)
        } else {
            db.cards().update(card)
            card.id
        }
        if (existing != null &&
            (existing.cutDay != card.cutDay || existing.cutDayInNextCycle != card.cutDayInNextCycle)
        ) {
            recomputeCycles(card)
        }
        return id
    }

    /** Reubica gastos, pagos y apartados cuando cambia el corte de la tarjeta. */
    /** Reubica gastos, pagos y apartados cuando cambia el corte de la tarjeta. */
    private suspend fun recomputeCycles(card: Card) {
        db.withTransaction {
            db.expenses().all()
                .filter { it.cardId == card.id }
                .forEach { expense ->
                    val date = LocalDate.ofEpochDay(expense.date)
                    val key = CycleMath.purchaseCycleKey(
                        date,
                        card.cutDay,
                        card.cutDayInNextCycle,
                    ).toEpochDay()
                    if (key != expense.cycleKey) {
                        db.expenses().update(expense.copy(cycleKey = key))
                    }
                }

            db.payments().all()
                .filter { it.cardId == card.id }
                .forEach { payment ->
                    val key = CycleMath.cycleKeyFor(
                        LocalDate.ofEpochDay(payment.date),
                        card.cutDay,
                    ).toEpochDay()
                    if (key != payment.cycleKey) {
                        db.payments().update(payment.copy(cycleKey = key))
                    }
                }

            db.covers().all()
                .filter { it.cardId == card.id }
                .forEach { cover ->
                    val key = CycleMath.cycleKeyFor(
                        LocalDate.ofEpochDay(cover.date),
                        card.cutDay,
                    ).toEpochDay()
                    if (key != cover.cycleKey) {
                        db.covers().delete(cover)
                        db.covers().insert(cover.copy(cycleKey = key))
                    }
                }
        }
    }

    suspend fun deleteCard(card: Card) {
        db.withTransaction {
            db.expenses().deleteForCard(card.id)
            db.payments().deleteForCard(card.id)
            db.covers().deleteForCard(card.id)
            db.plans().deleteForCard(card.id)
            db.recurring().all()
                .filter { it.cardId == card.id }
                .forEach { rule -> db.recurring().deleteCharges(rule.id) }
            db.recurring().deleteForCard(card.id)
            db.cards().delete(card)
        }
    }

    // ---- Categorías ----

    suspend fun saveCategory(category: Category): Long =
        if (category.id == 0L) db.categories().insert(category) else {
            db.categories().update(category)
            category.id
        }

    suspend fun deleteCategory(category: Category): Boolean {
        if (db.categories().expenseCount(category.id) > 0) return false
        db.categories().delete(category)
        return true
    }

    suspend fun categoryUsage(id: Long): Int = db.categories().expenseCount(id)

    // ---- Gastos ----

    /** Guarda un gasto y le asigna el ciclo según la fecha y el corte de la tarjeta. */
    suspend fun addExpense(
        cardId: Long,
        categoryId: Long,
        date: LocalDate,
        amountCents: Long,
        merchant: String,
        note: String = "",
        receiptPath: String? = null,
        sharedPercent: Int = 0,
    ): Long {
        val card = db.cards().byId(cardId) ?: error("Tarjeta no encontrada")
        val cycleKey = CycleMath.purchaseCycleKey(date, card.cutDay, card.cutDayInNextCycle).toEpochDay()
        return db.expenses().insert(
            Expense(
                cardId = cardId,
                categoryId = categoryId,
                date = date.toEpochDay(),
                cycleKey = cycleKey,
                amountCents = amountCents,
                merchant = merchant.trim(),
                note = note.trim(),
                receiptPath = receiptPath,
                sharedPercent = sharedPercent.coerceIn(0, 100),
            ),
        )
    }

    /**
     * Actualiza un gasto. Si [cycleOverride] viene con valor, se respeta ese ciclo
     * (permite mover una compra a otro mes); si no, se recalcula desde la fecha.
     */
    suspend fun updateExpense(expense: Expense, cycleOverride: Long? = null) {
        val card = db.cards().byId(expense.cardId) ?: return
        val date = LocalDate.ofEpochDay(expense.date)
        db.expenses().update(
            expense.copy(
                cycleKey = cycleOverride
                    ?: CycleMath.purchaseCycleKey(date, card.cutDay, card.cutDayInNextCycle).toEpochDay(),
                merchant = expense.merchant.trim(),
                note = expense.note.trim(),
            ),
        )
    }

    suspend fun deleteExpense(expense: Expense) = db.expenses().delete(expense)

    // ---- Pagos ----

    suspend fun addPayment(
        cardId: Long,
        cycleKey: LocalDate,
        date: LocalDate,
        amountCents: Long,
        note: String = "",
        receiptPath: String? = null,
    ): Long {
        return db.payments().insert(
            Payment(
                cardId = cardId,
                cycleKey = cycleKey.toEpochDay(),
                date = date.toEpochDay(),
                amountCents = amountCents,
                note = note.trim(),
                receiptPath = receiptPath,
            ),
        )
    }

    suspend fun deletePayment(payment: Payment) = db.payments().delete(payment)

    suspend fun updatePayment(payment: Payment) {
        db.payments().update(
            payment.copy(note = payment.note.trim()),
        )
    }

    // ---- En sobre ----

    /** Aparta dinero para un ciclo. Si viene de un sobre, lo descuenta. */
    suspend fun addCover(
        cardId: Long,
        cycleKey: Long,
        amountCents: Long,
        envelopeId: Long?,
        note: String = "",
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        if (amountCents <= 0) return false
        if (envelopeId != null) {
            val balance = db.envelopes().balanceUntil(envelopeId, date.toEpochDay())
            if (balance < amountCents) return false
            db.envelopes().insertTx(
                EnvelopeTx(
                    envelopeId = envelopeId,
                    date = date.toEpochDay(),
                    amountCents = -amountCents,
                    note = note.ifBlank { "Pago tarjeta" },
                    kind = "tarjeta",
                ),
            )
        }
        db.covers().insert(
            Cover(
                cardId = cardId,
                cycleKey = cycleKey,
                date = date.toEpochDay(),
                amountCents = amountCents,
                envelopeId = envelopeId,
                note = note.trim(),
            ),
        )
        return true
    }

    suspend fun deleteCover(cover: Cover) {
        db.covers().delete(cover)
        cover.envelopeId?.let { envelopeId ->
            db.envelopes().insertTx(
                EnvelopeTx(
                    envelopeId = envelopeId,
                    date = LocalDate.now().toEpochDay(),
                    amountCents = cover.amountCents,
                    note = "Devolución de apartado",
                    kind = "devolucion",
                ),
            )
        }
    }

    /** Saca dinero del "en sobre" de un ciclo (emergencias). */
    suspend fun withdrawCover(        cardId: Long,
        cycleKey: Long,
        amountCents: Long,
        note: String = "",
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        if (amountCents <= 0) return false
        val covered = db.covers().byCycle(cardId, cycleKey).sumOf { it.amountCents }
        if (amountCents > covered) return false
        db.covers().insert(
            Cover(
                cardId = cardId,
                cycleKey = cycleKey,
                date = date.toEpochDay(),
                amountCents = -amountCents,
                envelopeId = null,
                note = note.trim().ifBlank { "Retiro" },
            ),
        )
        return true
    }

    /** Edita un apartado. Si venía de un sobre, ajusta el saldo del sobre. */
    suspend fun updateCover(
        cover: Cover,
        amountCents: Long,
        date: LocalDate,
        note: String,
    ): Boolean {
        if (amountCents == 0L) return false

        // Retiros (monto negativo): se reemplazan directo.
        if (cover.envelopeId == null) {
            return runCatching {
                db.withTransaction {
                    db.covers().delete(cover)
                    db.covers().insert(
                        cover.copy(
                            amountCents = amountCents,
                            date = date.toEpochDay(),
                            note = note.trim(),
                        ),
                    )
                }
                true
            }.getOrDefault(false)
        }

        if (amountCents < 0) return false
        return runCatching {
            db.withTransaction {
                cover.envelopeId?.let { envelopeId ->
                    // devolver lo anterior y descontar lo nuevo
                    db.envelopes().insertTx(
                        EnvelopeTx(
                            envelopeId = envelopeId,
                            date = LocalDate.now().toEpochDay(),
                            amountCents = cover.amountCents,
                            note = "Ajuste de apartado",
                            kind = "devolucion",
                        ),
                    )
                    val balance = db.envelopes().balanceUntil(envelopeId, date.toEpochDay())
                    if (balance < amountCents) error("Saldo insuficiente en el sobre")
                    db.envelopes().insertTx(
                        EnvelopeTx(
                            envelopeId = envelopeId,
                            date = date.toEpochDay(),
                            amountCents = -amountCents,
                            note = "Ajuste de apartado",
                            kind = "tarjeta",
                        ),
                    )
                }
                db.covers().delete(cover)
                db.covers().insert(
                    cover.copy(
                        amountCents = amountCents,
                        date = date.toEpochDay(),
                        note = note.trim(),
                    ),
                )
            }
            true
        }.getOrDefault(false)
    }

    // ---- Sobres ----

    suspend fun saveEnvelope(envelope: Envelope): Long =
        if (envelope.id == 0L) db.envelopes().insert(envelope) else {
            db.envelopes().update(envelope)
            envelope.id
        }

    suspend fun deleteEnvelope(envelope: Envelope): Boolean {
        if (db.envelopes().txCount(envelope.id) > 0) return false
        db.envelopes().delete(envelope)
        return true
    }

    suspend fun moveEnvelopeMoney(
        envelopeId: Long,
        amountCents: Long,
        deposit: Boolean,
        note: String,
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        if (amountCents <= 0) return false
        if (!deposit) {
            val balance = db.envelopes().balanceUntil(envelopeId, date.toEpochDay())
            if (balance < amountCents) return false
        }
        db.envelopes().insertTx(
            EnvelopeTx(
                envelopeId = envelopeId,
                date = date.toEpochDay(),
                amountCents = if (deposit) amountCents else -amountCents,
                note = note.trim(),
                kind = if (deposit) "aporte" else "retiro",
            ),
        )
        return true
    }

    suspend fun transferBetweenEnvelopes(
        fromId: Long,
        toId: Long,
        amountCents: Long,
        note: String = "Transferencia",
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        if (amountCents <= 0 || fromId == toId) return false
        val balance = db.envelopes().balanceUntil(fromId, date.toEpochDay())
        if (balance < amountCents) return false
        db.envelopes().insertTx(
            EnvelopeTx(
                envelopeId = fromId,
                date = date.toEpochDay(),
                amountCents = -amountCents,
                note = note,
                kind = "transferencia",
            ),
        )
        db.envelopes().insertTx(
            EnvelopeTx(
                envelopeId = toId,
                date = date.toEpochDay(),
                amountCents = amountCents,
                note = note,
                kind = "transferencia",
            ),
        )
        return true
    }

    /** Borra un ciclo completo y devuelve al sobre el dinero que se había apartado. */
    suspend fun deleteCycle(cardId: Long, cycleKey: Long) {
        db.withTransaction {
            val covers = db.covers().byCycle(cardId, cycleKey)
            covers.forEach { cover ->
                cover.envelopeId?.let { envelopeId ->
                    db.envelopes().insertTx(
                        EnvelopeTx(
                            envelopeId = envelopeId,
                            date = LocalDate.now().toEpochDay(),
                            amountCents = cover.amountCents,
                            note = "Devolución por ciclo eliminado",
                            kind = "devolucion",
                        ),
                    )
                }
            }
            db.covers().deleteCycle(cardId, cycleKey)
            db.payments().deleteCycle(cardId, cycleKey)
            db.expenses().deleteCycle(cardId, cycleKey)
        }
    }

    // ---- Compras a tasa 0 ----

    /** Crea una compra a tasa 0 y reparte sus cuotas en los ciclos. */
    suspend fun createPlan(
        cardId: Long,
        categoryId: Long,
        merchant: String,
        totalCents: Long,
        months: Int,
        date: LocalDate,
        note: String = "",
    ): Long {
        require(totalCents > 0) { "Monto inválido" }
        require(months in 2..60) { "Cantidad de cuotas inválida" }
        val card = db.cards().byId(cardId) ?: return 0
        val plan = InstallmentPlan(
            cardId = cardId,
            categoryId = categoryId,
            merchant = merchant.trim(),
            totalCents = totalCents,
            months = months,
            firstDate = date.toEpochDay(),
            note = note.trim(),
        )
        var id = 0L
        db.withTransaction {
            id = db.plans().insert(plan)
            insertInstallments(plan.copy(id = id), card)
        }
        return id
    }

    suspend fun updatePlan(plan: InstallmentPlan): Boolean {
        val card = db.cards().byId(plan.cardId) ?: return false
        require(plan.totalCents > 0 && plan.months in 2..60)
        db.withTransaction {
            db.plans().update(plan)
            db.expenses().deletePlan(plan.id)
            insertInstallments(plan, card)
        }
        return true
    }

    /** Borra las cuotas del ciclo actual en adelante; las anteriores quedan como historial. */
    suspend fun deletePlan(plan: InstallmentPlan, fromCycle: LocalDate) {
        db.withTransaction {
            db.expenses().deletePlanFrom(plan.id, fromCycle.toEpochDay())
            db.plans().delete(plan)
        }
    }

    private suspend fun insertInstallments(plan: InstallmentPlan, card: Card) {
        val base = plan.totalCents / plan.months
        val remainder = plan.totalCents - base * plan.months
        val first = LocalDate.ofEpochDay(plan.firstDate)
        for (i in 0 until plan.months) {
            val date = first.plusMonths(i.toLong())
            val amount = base + if (i == plan.months - 1) remainder else 0L
            db.expenses().insert(
                Expense(
                    cardId = plan.cardId,
                    categoryId = plan.categoryId,
                    date = date.toEpochDay(),
                    cycleKey = CycleMath.purchaseCycleKey(
                        date,
                        card.cutDay,
                        card.cutDayInNextCycle,
                    ).toEpochDay(),
                    amountCents = amount,
                    merchant = plan.merchant,
                    note = plan.note,
                    planId = plan.id,
                    installmentIndex = i + 1,
                    installmentCount = plan.months,
                ),
            )
        }
    }

    // ---- Gastos fijos ----

    suspend fun saveRecurring(rule: RecurringExpense): Long {
        val card = db.cards().byId(rule.cardId) ?: return 0
        var id = rule.id
        val current = CycleMath.cycleKeyFor(LocalDate.now(), card.cutDay)
        db.withTransaction {
            if (id == 0L) {
                id = db.recurring().insert(rule)
            } else {
                db.recurring().update(rule)
                db.expenses().deleteRecurringFrom(rule.id, current.toEpochDay())
                db.recurring().deleteChargesFrom(rule.id, current.toEpochDay())
            }
        }
        materializeRecurring()
        return id
    }

    suspend fun setRecurringActive(rule: RecurringExpense, active: Boolean) {
        db.recurring().update(rule.copy(active = active))
        if (active) materializeRecurring()
    }

    suspend fun deleteRecurring(rule: RecurringExpense) {
        val card = db.cards().byId(rule.cardId)
        db.withTransaction {
            val current = card?.let { CycleMath.cycleKeyFor(LocalDate.now(), it.cutDay) }
                ?: LocalDate.now()
            db.expenses().deleteRecurringFrom(rule.id, current.toEpochDay())
            db.recurring().deleteCharges(rule.id)
            db.recurring().delete(rule)
        }
    }

    /** Genera los cobros pendientes de cada gasto fijo (hasta el ciclo siguiente). */
    suspend fun materializeRecurring() = recurringMutex.withLock {
        val today = LocalDate.now()
        val cardList = db.cards().all().associateBy { it.id }
        db.recurring().active().forEach { rule ->
            val card = cardList[rule.cardId] ?: return@forEach
            val current = CycleMath.cycleKeyFor(today, card.cutDay)
            // Se genera también el ciclo siguiente para que el próximo estado de cuenta ya lo incluya.
            val limit = CycleMath.nextCutKey(current, card.cutDay)
            val charged = db.recurring().chargeCycles(rule.id).toMutableSet()
            val expenseCycles = db.expenses().recurringCycles(rule.id).toSet()
            val first = LocalDate.ofEpochDay(rule.firstDate)

            var index = 0
            while (index < 60) {
                val date = first.plusMonths(index.toLong())
                val cycle = CycleMath.purchaseCycleKey(date, card.cutDay, card.cutDayInNextCycle)
                if (cycle.isAfter(limit)) break
                val key = cycle.toEpochDay()
                if (key !in charged) {
                    if (key in expenseCycles) {
                        // Ya existía de antes: solo marcamos el ciclo como generado.
                        db.recurring().insertCharge(RecurringCharge(rule.id, key))
                    } else {
                        db.expenses().insert(
                            Expense(
                                cardId = rule.cardId,
                                categoryId = rule.categoryId,
                                date = date.toEpochDay(),
                                cycleKey = key,
                                amountCents = rule.amountCents,
                                merchant = rule.merchant,
                                note = rule.note,
                                sharedPercent = rule.sharedPercent,
                                recurringId = rule.id,
                            ),
                        )
                        db.recurring().insertCharge(RecurringCharge(rule.id, key))
                    }
                    charged += key
                }
                index++
            }
        }
    }

    // ---- Respaldo ----

    /** Exporta todo a JSON para respaldar o cambiar de teléfono. */
    suspend fun exportJson(): String = Backup.toJson(
        BackupData(
            cards = db.cards().all(),
            categories = db.categories().all(),
            expenses = db.expenses().all(),
            payments = db.payments().all(),
            covers = db.covers().all(),
            envelopes = db.envelopes().all(),
            envelopeTxs = db.envelopes().allTxs(),
            plans = db.plans().all(),
            recurring = db.recurring().all(),
            recurringCharges = db.recurring().allCharges(),
        ),
    )

    /** Gastos en CSV para abrir en Excel o Google Sheets. */
    suspend fun exportCsv(): String {
        val cards = db.cards().all().associateBy { it.id }
        val categories = db.categories().all().associateBy { it.id }
        val expenses = db.expenses().all().sortedBy { it.date }

        val sb = StringBuilder("Fecha,Motivo,Monto,Categoria,Tarjeta,Ciclo,Nota\n")
        expenses.forEach { expense ->
            val cycle = LocalDate.ofEpochDay(expense.cycleKey)
            sb.append(csvCell(LocalDate.ofEpochDay(expense.date).toString())).append(',')
            sb.append(csvCell(expense.merchant)).append(',')
            sb.append(csvCell(csvAmount(expense.amountCents))).append(',')
            sb.append(csvCell(categories[expense.categoryId]?.name ?: "Sin categoría")).append(',')
            sb.append(csvCell(cards[expense.cardId]?.name ?: "")).append(',')
            sb.append(csvCell(cycle.toString())).append(',')
            sb.append(csvCell(expense.note)).append('\n')
        }
        return sb.toString()
    }

    private fun csvAmount(cents: Long): String =
        if (cents % 100L == 0L) (cents / 100).toString()
        else String.format(java.util.Locale.US, "%.2f", cents / 100.0)

    private fun csvCell(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }

    /** Reemplaza todos los datos por los del respaldo. */
    suspend fun importJson(json: String): Boolean = runCatching {
        val data = Backup.parse(json)
        db.withTransaction {
            db.expenses().clear()
            db.payments().clear()
            db.covers().clear()
            db.envelopes().clearTxs()
            db.envelopes().clear()
            db.plans().clear()
            db.recurring().clearCharges()
            db.recurring().clear()
            db.categories().clear()
            db.cards().clear()

            db.cards().insertAll(data.cards)
            db.categories().insertAll(data.categories)
            db.envelopes().insertAll(data.envelopes)
            db.plans().insertAll(data.plans)
            db.recurring().insertAll(data.recurring)
            db.recurring().insertAllCharges(data.recurringCharges)
            db.expenses().insertAll(data.expenses)
            db.payments().insertAll(data.payments)
            db.covers().insertAll(data.covers)
            db.envelopes().insertAllTxs(data.envelopeTxs)
        }
        true
    }.getOrDefault(false)

    /** Borra absolutamente todo: tarjetas, categorías con presupuestos, gastos, pagos y sobres. */
    suspend fun wipeAll() {        db.withTransaction {
            db.expenses().clear()
            db.payments().clear()
            db.covers().clear()
            db.envelopes().clearTxs()
            db.envelopes().clear()
            db.plans().clear()
            db.recurring().clearCharges()
            db.recurring().clear()
            db.categories().clear()
            db.cards().clear()
        }
    }

    // ---- Importar gastos ----

    /** Importa gastos/pagos y opcionalmente el "en sobre" de una hoja. */
    suspend fun importExpenses(
        cardId: Long,
        items: List<ImportedExpense>,
        defaultCategoryName: String?,
        payments: List<ImportedPayment> = emptyList(),
        coverCents: Long = 0L,
    ): ImportSummary {
        val card = db.cards().byId(cardId) ?: return ImportSummary()
        var added = 0
        var addedPayments = 0
        var coverApplied = false

        db.withTransaction {
            val map = db.categories().all().associateBy { CsvImport.normalize(it.name) }.toMutableMap()
            var order = map.size
            val mainCycle = items
                .mapNotNull { it.date }
                .groupingBy { CycleMath.purchaseCycleKey(it, card.cutDay, card.cutDayInNextCycle) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            items.forEach { item ->
                val name = item.categoryName?.takeIf { it.isNotBlank() }
                    ?: defaultCategoryName?.takeIf { it.isNotBlank() }
                    ?: "Importado"
                val key = CsvImport.normalize(name)
                val category = map[key] ?: run {
                    val created = Category(
                        name = name,
                        colorHex = IMPORT_COLORS[order % IMPORT_COLORS.size],
                        iconKey = iconFor(name),
                        sortOrder = order,
                    )
                    order++
                    created.copy(id = db.categories().insert(created)).also { map[key] = it }
                }

                val cycleKey = item.date
                    ?.let { CycleMath.purchaseCycleKey(it, card.cutDay, card.cutDayInNextCycle) }
                    ?: mainCycle
                    ?: CycleMath.nextCut(card.cutDay)
                val date = item.date ?: CycleMath.cycleStart(cycleKey, card.cutDay)

                db.expenses().insert(
                    Expense(
                        cardId = cardId,
                        categoryId = category.id,
                        date = date.toEpochDay(),
                        cycleKey = cycleKey.toEpochDay(),
                        amountCents = item.amountCents,
                        merchant = item.merchant,
                        note = item.note,
                    ),
                )
                added++
            }

            payments.forEach { payment ->
                val cycle = payment.date?.let { CycleMath.cycleKeyFor(it, card.cutDay) } ?: mainCycle
                if (cycle == null) return@forEach
                db.payments().insert(
                    Payment(
                        cardId = cardId,
                        cycleKey = cycle.toEpochDay(),
                        date = (payment.date ?: cycle).toEpochDay(),
                        amountCents = payment.amountCents,
                        note = payment.note.ifBlank { "Pago importado" },
                    ),
                )
                addedPayments++
            }

            if (coverCents > 0 && mainCycle != null) {                db.covers().insert(
                    Cover(
                        cardId = cardId,
                        cycleKey = mainCycle.toEpochDay(),
                        date = LocalDate.now().toEpochDay(),
                        amountCents = coverCents,
                        envelopeId = null,
                        note = "En sobre importado",
                    ),
                )
                coverApplied = true
            }
        }
        return ImportSummary(added, addedPayments, coverApplied)
    }

    private companion object {
        val IMPORT_COLORS = listOf(
            "#1F5C4B", "#D9A441", "#3A6B8C", "#A64B7A", "#7A5CC4",
            "#C0603A", "#4E8C5A", "#B5892F", "#5B6B7A", "#8C4E4E",
        )

        fun iconFor(name: String): String = when (CsvImport.normalize(name)) {
            "comidas", "comida", "restaurantes" -> "comida"
            "super", "supermercado" -> "super"
            "salidas", "entretenimiento" -> "salidas"
            "combustible", "gasolina", "transporte" -> "transporte"
            "peajes" -> "transporte"
            "salud", "farmacia", "medicinas" -> "salud"
            "hogar", "casa" -> "hogar"
            "mascotas" -> "mascotas"
            "ropa" -> "ropa"
            "servicios" -> "servicios"
            else -> "otros"
        }
    }
}
