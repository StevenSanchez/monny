package cr.monny.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cr.monny.data.AppSettings
import cr.monny.data.Card
import cr.monny.data.Category
import cr.monny.data.Cover
import cr.monny.data.CycleData
import cr.monny.data.Envelope
import cr.monny.data.EnvelopeBalance
import cr.monny.data.Expense
import cr.monny.data.InstallmentPlan
import cr.monny.data.MonnyRepository
import cr.monny.data.Payment
import cr.monny.data.RecurringExpense
import cr.monny.data.SharedPayment
import cr.monny.domain.CycleMath
import cr.monny.domain.ImportedExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * Estado y acciones de la UI.
 *
 * Un único ViewModel (con alcance de Activity) alimenta todas las pantallas:
 * tarjeta seleccionada, ciclo actual, historial, sobres, cuotas y gastos fijos.
 * Toda la información se deriva de los Flow del repositorio, así que cualquier
 * cambio se refleja en todas las pantallas al mismo tiempo.
 */

/** Total gastado en una categoría dentro de un ciclo. */
data class CategoryTotal(
    val category: Category,
    val totalCents: Long,
    val count: Int,
)

/** Todo lo que necesita la pantalla de Inicio para el ciclo actual. */
data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val card: Card? = null,
    val cutKey: LocalDate = LocalDate.now(),
    val cycleStart: LocalDate = LocalDate.now(),
    val cycleEnd: LocalDate = LocalDate.now(),
    val paymentDue: LocalDate = LocalDate.now(),
    val daysToCut: Long = 0,
    val categories: List<Category> = emptyList(),
    val totalCents: Long = 0,
    val paidCents: Long = 0,
    val coveredCents: Long = 0,
    val expenses: List<Expense> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val covers: List<Cover> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
) {
    /** Lo que falta por cubrir del ciclo (negativo = a favor). */
    val remainingCents: Long get() = totalCents - paidCents - coveredCents

    val progress: Float
        get() = if (totalCents <= 0) 0f
        else ((paidCents + coveredCents).toFloat() / totalCents).coerceIn(0f, 1f)

    fun categoryOf(id: Long): Category =
        categories.firstOrNull { it.id == id } ?: Category(id = id, name = "Sin categoría", colorHex = "#8A8F8B")
}

/** Fila del historial: un ciclo cerrado o el próximo. */
data class CycleRow(
    val cutKey: LocalDate,
    val startKey: LocalDate,
    val totalCents: Long,
    val paidCents: Long,
    val coveredCents: Long,
    val count: Int,
) {
    val remainingCents: Long get() = totalCents - paidCents - coveredCents
}

/** Detalle de un ciclo específico abierto desde el historial. */
data class CycleDetailState(
    val card: Card,
    val cutKey: LocalDate,
    val start: LocalDate,
    val end: LocalDate,
    val due: LocalDate,
    val totalCents: Long,
    val paidCents: Long,
    val coveredCents: Long,
    val expenses: List<Expense>,
    val categories: List<Category>,
    val payments: List<Payment>,
    val covers: List<Cover>,
) {
    val remainingCents: Long get() = totalCents - paidCents - coveredCents
}

/** Sobre con su saldo actual. */
data class EnvelopeRow(
    val envelope: Envelope,
    val balanceCents: Long,
)

/** Compra a tasa 0 con su progreso. */
data class PlanRow(    val plan: InstallmentPlan,
    val charged: Int,
) {
    val remaining: Int get() = (plan.months - charged).coerceAtLeast(0)
    val currentInstallment: Int get() = (charged + 1).coerceAtMost(plan.months)
}

/** Ciclo ya cerrado que todavía tiene saldo por pagar. */
data class PendingPayment(
    val cutKey: LocalDate,
    val startKey: LocalDate,
    val due: LocalDate,
    val totalCents: Long,
    val paidCents: Long,
    val coveredCents: Long,
) {
    val remainingCents: Long get() = totalCents - paidCents - coveredCents
}

/**
 * ViewModel compartido de Monny. Expone flows derivados (Inicio, historial,
 * detalle de ciclo, sobres, cuotas) y funciones para cada acción del usuario.
 */
class MonnyViewModel(
    private val repo: MonnyRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _selectedCardId = MutableStateFlow(settings.lastCardId)
    val selectedCardId: StateFlow<Long> = _selectedCardId.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val cards: StateFlow<List<Card>> = repo.cards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** false hasta que la base de datos entrega los datos por primera vez. */
    val ready: StateFlow<Boolean> = repo.cards
        .map { true }
        .onStart { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val envelopes: StateFlow<List<EnvelopeRow>> = combine(
        repo.envelopes,
        repo.envelopeBalances,
    ) { list, balances ->
        val map: Map<Long, Long> = balances.associate { it.envelopeId to it.balance }
        list.map { EnvelopeRow(it, map[it.id] ?: 0L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val merchantHints: StateFlow<List<String>> = repo.merchantHints
        .map { hints -> hints.map { it.merchant } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sharedEnabled: StateFlow<Boolean> = settings.sharedEnabled

    val allExpenses: StateFlow<List<Expense>> = repo.observeEverything()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurring: StateFlow<List<RecurringExpense>> = repo.recurring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sharedPayments: StateFlow<List<SharedPayment>> = repo.sharedPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plans: StateFlow<List<PlanRow>> = combine(
        repo.plans,
        repo.observeEverything(),
        cards,
    ) { planList, expenses, cardList ->
        val byPlan = expenses.filter { it.planId != null }.groupBy { it.planId!! }
        planList.map { plan ->
            val card = cardList.firstOrNull { it.id == plan.cardId }
            val current = card?.let { CycleMath.nextCut(it.cutDay) }
            val charged = if (current == null) {
                0
            } else {
                byPlan[plan.id]?.count { it.cycleKey < current.toEpochDay() } ?: 0
            }
            PlanRow(plan, charged)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCard: StateFlow<Card?> = combine(cards, _selectedCardId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val cutKey: Flow<LocalDate?> = combine(selectedCard, _today) { card, today ->
        card?.let { CycleMath.nextCut(it.cutDay, today) }
    }

    private val cycleData: Flow<CycleData> = combine(_selectedCardId, cutKey) { id, key -> id to key }
        .flatMapLatest { (id, key) ->
            if (id == 0L || key == null) flowOf(CycleData()) else repo.observeCycle(id, key.toEpochDay())
        }

    val home: StateFlow<HomeState> = combine(
        selectedCard,
        cutKey,
        _today,
        categories,
        cycleData,
    ) { card, key, today, cats, data ->
        if (card == null || key == null) {
            HomeState(today = today, categories = cats)
        } else {
            buildHome(card, key, today, cats, data)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val history: StateFlow<List<CycleRow>> = _selectedCardId
        .flatMapLatest { id ->
            if (id == 0L) {
                flowOf(emptyList())
            } else {
                combine(
                    repo.observeAllExpenses(id),
                    repo.observePaymentsForCard(id),
                    repo.observeCoversForCard(id),
                    cards,
                ) { expenses, payments, covers, cardList ->
                    val cutDay = cardList.firstOrNull { it.id == id }?.cutDay ?: 1
                    val paid = payments.groupBy { it.cycleKey }.mapValues { e -> e.value.sumOf { it.amountCents } }
                    val covered = covers.groupBy { it.cycleKey }.mapValues { e -> e.value.sumOf { it.amountCents } }
                    expenses.groupBy { it.cycleKey }
                        .map { (key, list) ->
                            val cutKey = LocalDate.ofEpochDay(key)
                            CycleRow(
                                cutKey = cutKey,
                                startKey = CycleMath.cycleStart(cutKey, cutDay),
                                totalCents = list.sumOf { it.amountCents },
                                paidCents = paid[key] ?: 0L,
                                coveredCents = covered[key] ?: 0L,
                                count = list.size,
                            )
                        }
                        .sortedByDescending { it.cutKey }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val detailTarget = MutableStateFlow<Pair<Long, Long>?>(null)

    val cycleDetail: StateFlow<CycleDetailState?> = detailTarget
        .flatMapLatest { target ->
            if (target == null) {
                flowOf(null)
            } else {
                combine(
                    repo.observeCycle(target.first, target.second),
                    repo.cards,
                    repo.categories,
                ) { data, cards, cats ->
                    val card = cards.firstOrNull { it.id == target.first } ?: return@combine null
                    val key = LocalDate.ofEpochDay(target.second)
                    CycleDetailState(
                        card = card,
                        cutKey = key,
                        start = CycleMath.cycleStart(key, card.cutDay),
                        end = key,
                        due = CycleMath.paymentDue(key, card.paymentDueDays),
                        totalCents = data.expenses.sumOf { it.amountCents },
                        paidCents = data.payments.sumOf { it.amountCents },
                        coveredCents = data.covers.sumOf { it.amountCents },
                        expenses = data.expenses,
                        categories = cats,
                        payments = data.payments,
                        covers = data.covers,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Último ciclo cerrado que sigue con saldo. Mientras exista, Inicio muestra
     * "Pago pendiente" y el botón Pagar apunta a ese ciclo (no al nuevo).
     */
    val pendingPayment: StateFlow<PendingPayment?> = combine(selectedCard, _today, history) { card, today, rows ->
        if (card == null) {
            null
        } else {
            val last = CycleMath.lastCut(card.cutDay, today)
            rows.firstOrNull { it.cutKey == last && it.remainingCents > 0 }?.let { row ->
                PendingPayment(
                    cutKey = row.cutKey,
                    startKey = row.startKey,
                    due = CycleMath.paymentDue(row.cutKey, card.paymentDueDays),
                    totalCents = row.totalCents,
                    paidCents = row.paidCents,
                    coveredCents = row.coveredCents,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Mantiene una tarjeta seleccionada válida y genera los gastos fijos pendientes.
        viewModelScope.launch {
            cards.collect { list ->
                val current = _selectedCardId.value
                if (list.isNotEmpty() && list.none { it.id == current }) {
                    _selectedCardId.value = list.first().id
                }
                if (list.isEmpty()) _selectedCardId.value = 0L
            }
        }
        viewModelScope.launch { repo.materializeRecurring() }
    }

    private fun buildHome(
        card: Card,
        key: LocalDate,
        today: LocalDate,
        cats: List<Category>,
        data: CycleData,
    ): HomeState {
        val totals = data.expenses.groupBy { it.categoryId }
            .map { (categoryId, list) ->
                CategoryTotal(
                    category = cats.firstOrNull { it.id == categoryId }
                        ?: Category(id = categoryId, name = "Sin categoría", colorHex = "#8A8F8B"),
                    totalCents = list.sumOf { it.amountCents },
                    count = list.size,
                )
            }
            .sortedByDescending { it.totalCents }

        return HomeState(
            today = today,
            card = card,
            cutKey = key,
            cycleStart = CycleMath.cycleStart(key, card.cutDay),
            cycleEnd = key,
            paymentDue = CycleMath.paymentDue(key, card.paymentDueDays),
            daysToCut = CycleMath.daysToCut(card.cutDay, today),
            categories = cats,
            totalCents = data.expenses.sumOf { it.amountCents },
            paidCents = data.payments.sumOf { it.amountCents },
            coveredCents = data.covers.sumOf { it.amountCents },
            expenses = data.expenses,
            payments = data.payments,
            covers = data.covers,
            categoryTotals = totals,
        )
    }

    // ---- Acciones ----

    fun observeEnvelopeTxs(envelopeId: Long): StateFlow<List<cr.monny.data.EnvelopeTx>> =
        repo.observeEnvelopeTxs(envelopeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshToday() {
        _today.value = LocalDate.now()
        viewModelScope.launch { repo.materializeRecurring() }
    }

    fun selectCard(id: Long) {
        _selectedCardId.value = id
        settings.lastCardId = id
    }

    fun openCycle(cardId: Long, cutKey: LocalDate) {
        detailTarget.value = cardId to cutKey.toEpochDay()
    }

    fun addExpense(
        cardId: Long,
        categoryId: Long,
        date: LocalDate,
        amountCents: Long,
        merchant: String,
        note: String,
        receiptPath: String?,
        sharedPercent: Int = 0,
    ) {
        viewModelScope.launch {
            repo.addExpense(cardId, categoryId, date, amountCents, merchant, note, receiptPath, sharedPercent)
            notify("Gasto guardado")
        }
    }

    fun updateExpense(expense: Expense, cycleOverride: LocalDate? = null) {
        viewModelScope.launch {
            repo.updateExpense(expense, cycleOverride?.toEpochDay())
            notify("Gasto actualizado")
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repo.deleteExpense(expense)
            notify("Gasto eliminado")
        }
    }

    fun setSharedEnabled(enabled: Boolean) {
        settings.setSharedEnabled(enabled)
    }

    fun updatePayment(payment: Payment, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.updatePayment(payment)
            notify("Pago actualizado")
            onDone()
        }
    }

    fun updateCover(
        cover: Cover,
        amountCents: Long,
        date: LocalDate,
        note: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.updateCover(cover, amountCents, date, note)
            notify(if (ok) "Apartado actualizado" else "No se pudo actualizar el apartado")
            onResult(ok)
        }
    }

    fun createPlan(
        cardId: Long,
        categoryId: Long,
        merchant: String,
        totalCents: Long,
        months: Int,
        date: LocalDate,
        note: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repo.createPlan(cardId, categoryId, merchant, totalCents, months, date, note)
            notify("Compra a tasa 0 creada")
            onDone()
        }
    }

    fun updatePlan(plan: InstallmentPlan, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.updatePlan(plan)
            notify("Compra actualizada")
            onDone()
        }
    }

    fun deletePlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            val card = repo.card(plan.cardId)
            val from = card?.let { CycleMath.nextCut(it.cutDay) } ?: LocalDate.now()
            repo.deletePlan(plan, from)
            notify("Cuotas futuras eliminadas")
        }
    }

    fun saveRecurring(rule: RecurringExpense, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveRecurring(rule)
            notify("Gasto fijo guardado")
            onDone()
        }
    }

    fun setRecurringActive(rule: RecurringExpense, active: Boolean) {
        viewModelScope.launch { repo.setRecurringActive(rule, active) }
    }

    fun deleteRecurring(rule: RecurringExpense) {
        viewModelScope.launch {
            repo.deleteRecurring(rule)
            notify("Gasto fijo eliminado")
        }
    }

    fun addPayment(
        cardId: Long,
        cycleKey: LocalDate,
        date: LocalDate,
        amountCents: Long,
        note: String,
        receiptPath: String?,
    ) {
        viewModelScope.launch {
            repo.addPayment(cardId, cycleKey, date, amountCents, note, receiptPath)
            notify("Pago registrado")
        }
    }

    fun addCover(
        cardId: Long,
        cycleKey: LocalDate,
        amountCents: Long,
        envelopeId: Long?,
        note: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.addCover(
                cardId = cardId,
                cycleKey = cycleKey.toEpochDay(),
                amountCents = amountCents,
                envelopeId = envelopeId,
                note = note,
            )
            notify(if (ok) "Dinero apartado" else "El sobre no tiene saldo suficiente")
            onResult(ok)
        }
    }

    fun deleteCover(cover: Cover) {
        viewModelScope.launch {
            repo.deleteCover(cover)
            notify("Apartado eliminado")
        }
    }

    fun withdrawCover(
        cardId: Long,
        cycleKey: LocalDate,
        amountCents: Long,
        note: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.withdrawCover(cardId, cycleKey.toEpochDay(), amountCents, note)
            notify(if (ok) "Retiro registrado" else "No hay suficiente en el sobre")
            onResult(ok)
        }
    }

    fun addSharedPayment(
        cardId: Long,
        cycleKey: LocalDate,
        amountCents: Long,
        envelopeId: Long?,
        note: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.addSharedPayment(
                cardId = cardId,
                cycleKey = cycleKey.toEpochDay(),
                amountCents = amountCents,
                envelopeId = envelopeId,
                note = note,
            )
            notify(if (ok) "Abono registrado" else "El monto supera lo que te deben")
            onResult(ok)
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repo.deletePayment(payment)
            notify("Pago eliminado")
        }
    }

    fun saveCard(card: Card, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveCard(card)
            if (_selectedCardId.value == 0L) selectCard(id)
            notify("Tarjeta guardada")
            onDone()
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch {
            repo.deleteCard(card)
            notify("Tarjeta eliminada")
        }
    }

    fun deleteCycle(cardId: Long, cycleKey: LocalDate, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteCycle(cardId, cycleKey.toEpochDay())
            notify("Ciclo eliminado")
            onDone()
        }
    }

    fun saveCategory(category: Category, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveCategory(category)
            onDone(id)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            val ok = repo.deleteCategory(category)
            notify(if (ok) "Categoría eliminada" else "No se puede: tiene gastos asociados")
        }
    }

    fun saveEnvelope(envelope: Envelope, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveEnvelope(envelope)
            onDone()
        }
    }

    fun deleteEnvelope(envelope: Envelope) {
        viewModelScope.launch {
            val ok = repo.deleteEnvelope(envelope)
            notify(if (ok) "Sobre eliminado" else "No se puede: tiene movimientos")
        }
    }

    fun moveEnvelopeMoney(
        envelopeId: Long,
        amountCents: Long,
        deposit: Boolean,
        note: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.moveEnvelopeMoney(envelopeId, amountCents, deposit, note)
            notify(if (ok) "Movimiento registrado" else "Saldo insuficiente")
            onResult(ok)
        }
    }

    fun transferBetweenEnvelopes(
        fromId: Long,
        toId: Long,
        amountCents: Long,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = repo.transferBetweenEnvelopes(fromId, toId, amountCents)
            notify(if (ok) "Transferencia hecha" else "Saldo insuficiente")
            onResult(ok)
        }
    }

    // ---- Respaldo e importación ----

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repo.exportJson()) }
    }

    fun exportExpensesCsv(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repo.exportCsv()) }
    }

    fun importBackup(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.importJson(json)
            notify(if (ok) "Respaldo restaurado" else "No se pudo restaurar el respaldo")
            onDone(ok)
        }
    }

    fun wipeAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.wipeAll()
            settings.lastCardId = 0L
            _selectedCardId.value = 0L
            notify("Datos eliminados")
            onDone()
        }
    }

    fun importExpenses(
        cardId: Long,
        items: List<ImportedExpense>,
        payments: List<cr.monny.domain.ImportedPayment>,
        coverCents: Long,
        defaultCategory: String?,
        onDone: (cr.monny.data.ImportSummary) -> Unit,
    ) {
        viewModelScope.launch {
            val summary = repo.importExpenses(
                cardId = cardId,
                items = items,
                defaultCategoryName = defaultCategory,
                payments = payments,
                coverCents = coverCents,
            )
            val coverText = if (summary.coverApplied) " y el \"en sobre\"" else ""
            notify("Se importaron ${summary.expenses} gastos$coverText")
            onDone(summary)
        }
    }

    private fun notify(message: String) {
        _messages.tryEmit(message)
    }

    class Factory(
        private val repo: MonnyRepository,
        private val settings: AppSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MonnyViewModel(repo, settings) as T
    }
}
