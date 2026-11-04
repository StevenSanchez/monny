package cr.monny.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.CycleDetailState
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.CategoryTotalRow
import cr.monny.ui.components.ConfirmDialog
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.ExpenseRow
import cr.monny.ui.components.KeyValueRow
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField
import cr.monny.ui.components.MoneyText
import cr.monny.ui.theme.LocalMonnyColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Detalle de un ciclo: resumen, categorías, pagos, apartados y gastos editables. */
@Composable
fun CycleDetailScreen(
    vm: MonnyViewModel,
    cardId: Long,
    cycleKeyEpoch: Long,
    onBack: () -> Unit,
    onPay: () -> Unit,
    onEditExpense: (cr.monny.data.Expense) -> Unit,
) {
    LaunchedEffect(cardId, cycleKeyEpoch) {
        vm.openCycle(cardId, LocalDate.ofEpochDay(cycleKeyEpoch))
    }
    val detail by vm.cycleDetail.collectAsStateWithLifecycle()

    val state = detail
    if (state == null) {
        Column {
            MonnyTopBar("Ciclo", onBack = onBack)
            EmptyState(
                icon = Icons.Rounded.History,
                title = "Cargando",
                message = "Un momento…",
            )
        }
        return
    }

    val extra = LocalMonnyColors.current
    val grouped = state.expenses.groupBy { it.date }.toSortedMap(compareByDescending { it })
    var showDelete by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<cr.monny.data.Payment?>(null) }
    var editingCover by remember { mutableStateOf<cr.monny.data.Cover?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonnyTopBar(
                title = CycleMath.monthLabel(state.start),
                subtitle = state.card.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Eliminar ciclo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "GASTADO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(state.totalCents),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${CycleMath.shortDate(state.start)} – ${CycleMath.shortDate(state.end)} · " +
                            "pago ${CycleMath.shortDate(state.due)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow("En sobre", Money.format(state.coveredCents))
                    KeyValueRow("Pagado", Money.format(state.paidCents))
                    KeyValueRow(
                        label = if (state.remainingCents >= 0) "Falta" else "A favor",
                        value = Money.format(kotlin.math.abs(state.remainingCents)),
                        valueColor = if (state.remainingCents > 0) extra.negative else extra.positive,
                        emphasis = true,
                    )
                }
            }
        }

        if (state.expenses.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Por categoría", style = MaterialTheme.typography.titleMedium)
                        state.expenses.groupBy { it.categoryId }
                            .map { (categoryId, list) ->
                                val category = state.categories.firstOrNull { it.id == categoryId }
                                Triple(category, list.sumOf { it.amountCents }, list.size)
                            }
                            .sortedByDescending { it.second }
                            .forEach { (category, total, count) ->
                                CategoryTotalRow(
                                    name = category?.name ?: "Sin categoría",
                                    color = cr.monny.ui.theme.hexToColor(category?.colorHex ?: "#8A8F8B"),
                                    amountCents = total,
                                    share = if (state.totalCents > 0) total.toFloat() / state.totalCents else 0f,
                                    count = count,
                                    budgetCents = category?.budgetCents ?: 0L,
                                )
                            }
                    }
                }
            }
        }

        if (state.payments.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Pagos", style = MaterialTheme.typography.titleMedium)
                        state.payments.forEach { payment ->
                            MovementRow(
                                label = CycleMath.shortDate(LocalDate.ofEpochDay(payment.date)) +
                                    payment.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                amountCents = payment.amountCents,
                                onClick = { editingPayment = payment },
                            )
                        }
                    }
                }
            }
        }

        if (state.covers.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Apartados", style = MaterialTheme.typography.titleMedium)
                        state.covers.forEach { cover ->
                            MovementRow(
                                label = CycleMath.shortDate(LocalDate.ofEpochDay(cover.date)) +
                                    cover.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                amountCents = kotlin.math.abs(cover.amountCents),
                                negative = cover.amountCents < 0,
                                onClick = { editingCover = cover },
                            )
                        }
                    }
                }
            }
        }

        if (state.expenses.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Payments,
                    title = "Ciclo vacío",
                    message = "No hay gastos registrados en este ciclo.",
                )
            }
        }

        grouped.forEach { (day, expenses) ->
            item(key = "day-$day") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        CycleMath.longDate(LocalDate.ofEpochDay(day)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(expenses.sumOf { it.amountCents }),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(expenses, key = { it.id }) { expense ->
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                        ExpenseRow(
                            expense = expense,
                            category = state.categories.firstOrNull { it.id == expense.categoryId },
                            showDate = false,
                            onClick = { onEditExpense(expense) },
                        )
                    }
                }
            }
        }

        if (state.remainingCents > 0) {
            item {
                Button(
                    onClick = onPay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Registrar pago de ${Money.format(state.remainingCents)}")
                }
            }
        }
    }

    editingPayment?.let { payment ->
        MovementEditDialog(
            title = "Editar pago",
            amountCents = payment.amountCents,
            date = LocalDate.ofEpochDay(payment.date),
            note = payment.note,
            onDismiss = { editingPayment = null },
            onDelete = {
                vm.deletePayment(payment)
                editingPayment = null
            },
            onSave = { cents, newDate, newNote ->
                vm.updatePayment(
                    payment.copy(
                        amountCents = cents,
                        date = newDate.toEpochDay(),
                        note = newNote,
                    ),
                ) { editingPayment = null }
            },
        )
    }

    editingCover?.let { cover ->
        MovementEditDialog(
            title = if (cover.amountCents < 0) "Editar retiro" else "Editar apartado",
            amountCents = kotlin.math.abs(cover.amountCents),
            date = LocalDate.ofEpochDay(cover.date),
            note = cover.note,
            onDismiss = { editingCover = null },
            onDelete = {
                vm.deleteCover(cover)
                editingCover = null
            },
            onSave = { cents, newDate, newNote ->
                val signed = if (cover.amountCents < 0) -cents else cents
                vm.updateCover(cover, signed, newDate, newNote) { ok ->
                    if (ok) editingCover = null
                }
            },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Eliminar ${CycleMath.monthLabel(state.start)}",
            message = buildString {
                append("Se van a borrar ")
                append("${state.expenses.size} ${if (state.expenses.size == 1) "gasto" else "gastos"}")
                if (state.payments.isNotEmpty()) {
                    append(", ${state.payments.size} ${if (state.payments.size == 1) "pago" else "pagos"}")
                }
                if (state.covers.isNotEmpty()) {
                    append(", ${state.covers.size} ${if (state.covers.size == 1) "apartado" else "apartados"}")
                }
                append(" del ciclo de ${CycleMath.monthLabel(state.start)}. ")
                if (state.covers.isNotEmpty()) {
                    append("El dinero apartado vuelve a su sobre. ")
                }
                append("Esta acción no se puede deshacer.")
            },
            onConfirm = {
                vm.deleteCycle(state.card.id, state.cutKey) { onBack() }
            },
            onDismiss = { showDelete = false },
        )
    }
}

/** Fila tocable de un pago o apartado (los retiros se muestran en rojo). */
@Composable
private fun MovementRow(
    label: String,
    amountCents: Long,
    onClick: () -> Unit,
    negative: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                (if (negative) "-" else "") + Money.format(amountCents),
                style = MaterialTheme.typography.bodyMedium,
                color = if (negative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
    }
}

/** Diálogo para editar monto, fecha y nota de un pago o apartado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementEditDialog(
    title: String,
    amountCents: Long,
    date: LocalDate,
    note: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Long, LocalDate, String) -> Unit,
) {
    var amount by remember { mutableStateOf(Money.toDigits(amountCents)) }
    var noteText by remember { mutableStateOf(note) }
    var dateValue by remember { mutableStateOf(date) }
    var showPicker by remember { mutableStateOf(false) }
    val cents = Money.parse(amount) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Monto")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPicker = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Fecha",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(CycleMath.longDate(dateValue), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = cents > 0, onClick = { onSave(cents, dateValue, noteText) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateValue.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        dateValue = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = pickerState) }
    }
}
