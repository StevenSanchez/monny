package cr.monny.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Category
import cr.monny.data.RecurringExpense
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.ConfirmDialog
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Gastos fijos mensuales (seguros, suscripciones) con pausa y edición. */
@Composable
fun RecurringScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val rules by vm.recurring.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var deleting by remember { mutableStateOf<RecurringExpense?>(null) }

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Gastos fijos",
            subtitle = "Se agregan solos cada ciclo",
            onBack = onBack,
            actions = {
                TextButton(onClick = { creating = true }) { Text("Nuevo") }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Sin gastos fijos",
                        message = "Agregá el seguro de la tarjeta, suscripciones o cualquier cobro que " +
                            "se repita todos los meses. Se irán creando solos en cada ciclo.",
                        actionLabel = "Agregar gasto fijo",
                        onAction = { creating = true },
                    )
                }
            }

            items(rules, key = { it.id }) { rule ->
                val category = categories.firstOrNull { it.id == rule.categoryId }
                val day = LocalDate.ofEpochDay(rule.firstDate).dayOfMonth
                MonnyCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = rule },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.merchant, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${category?.name ?: "Sin categoría"} · día $day de cada mes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Money.format(rule.amountCents),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Switch(
                                checked = rule.active,
                                onCheckedChange = { vm.setRecurringActive(rule, it) },
                            )
                        }
                    }
                }
            }

            if (rules.isNotEmpty()) {
                item {
                    Text(
                        "Los gastos fijos se crean en cada ciclo mientras estén activos. " +
                            "Podés editar el monto o eliminarlos cuando quieras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        RecurringDialog(
            initial = editing,
            categories = categories,
            cardId = home.card?.id,
            cardName = home.card?.name,
            onDismiss = {
                creating = false
                editing = null
            },
            onDelete = editing?.let { rule ->
                {
                    editing = null
                    deleting = rule
                }
            },
            onSave = { rule ->
                vm.saveRecurring(rule) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Eliminar ${rule.merchant}",
            message = "Se van a borrar los cobros del ciclo actual en adelante. " +
                "Los anteriores quedan en el historial.",
            onConfirm = { vm.deleteRecurring(rule) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringDialog(
    initial: RecurringExpense?,
    categories: List<Category>,
    cardId: Long?,
    cardName: String?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (RecurringExpense) -> Unit,
) {
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var amount by remember {
        mutableStateOf(initial?.let { Money.toDigits(it.amountCents) } ?: "")
    }
    var categoryId by remember {
        mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var date by remember {
        mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.firstDate) } ?: LocalDate.now())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val cents = Money.parse(amount) ?: 0L
    val targetCard = initial?.cardId ?: cardId ?: 0L
    val valid = merchant.isNotBlank() && cents > 0 && categoryId != 0L && targetCard != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo gasto fijo" else "Editar gasto fijo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio o motivo") },
                    placeholder = { Text("Ej. Seguro de la tarjeta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                MoneyField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Monto mensual",
                )
                Spacer(Modifier.height(10.dp))
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Primer cobro",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(CycleMath.longDate(date), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (cardName != null) {
                    Text(
                        "Se carga en $cardName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) {
                        Text("Eliminar gasto fijo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        (initial ?: RecurringExpense(
                            cardId = targetCard,
                            categoryId = categoryId,
                            merchant = "",
                            amountCents = 0,
                            firstDate = 0,
                        )).copy(
                            cardId = targetCard,
                            merchant = merchant.trim(),
                            amountCents = cents,
                            categoryId = categoryId,
                            firstDate = date.toEpochDay(),
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = pickerState) }
    }
}
