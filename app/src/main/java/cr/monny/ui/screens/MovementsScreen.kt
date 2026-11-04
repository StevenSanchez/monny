package cr.monny.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import cr.monny.data.Expense
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.ExpenseRow
import cr.monny.ui.components.KeyValueRow
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.Pill
import cr.monny.ui.theme.LocalMonnyColors
import java.time.LocalDate

/** Lista de gastos del ciclo, agrupada por día y con filtro por categoría. */
@Composable
fun MovementsScreen(
    vm: MonnyViewModel,
    onEdit: (Expense) -> Unit,
    onHistory: () -> Unit,
) {
    val home by vm.home.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<Long?>(null) }

    if (home.card == null) {
        EmptyState(
            icon = Icons.Rounded.ReceiptLong,
            title = "Todavía no hay nada",
            message = "Creá una tarjeta para empezar a registrar gastos.",
        )
        return
    }

    val filtered = remember(home.expenses, filter) {
        if (filter == null) home.expenses else home.expenses.filter { it.categoryId == filter }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }
    val categoriesUsed = remember(home.expenses) { home.expenses.map { it.categoryId }.distinct() }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Gastos", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${home.card?.name} · ${CycleMath.monthLabel(home.cycleStart)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "Total del ciclo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                Money.format(home.totalCents),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        val extra = LocalMonnyColors.current
                        Pill(
                            text = when {
                                home.remainingCents > 0 -> "Faltan ${Money.format(home.remainingCents)}"
                                home.remainingCents < 0 -> "A favor ${Money.format(-home.remainingCents)}"
                                else -> "Al día"
                            },
                            color = if (home.remainingCents > 0) extra.negative else extra.positive,
                            background = if (home.remainingCents > 0) extra.negativeContainer else extra.positiveContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("En sobre", Money.format(home.coveredCents))
                    KeyValueRow("Pagado", Money.format(home.paidCents))
                }
            }
        }

        if (categoriesUsed.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("Todas") },
                        )
                    }
                    items(categoriesUsed, key = { it }) { id ->
                        val category: Category? = home.categories.firstOrNull { it.id == id }
                        FilterChip(
                            selected = filter == id,
                            onClick = { filter = if (filter == id) null else id },
                            label = { Text(category?.name ?: "Otra") },
                        )
                    }
                }
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Sin gastos",
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
                            category = home.categoryOf(expense.categoryId),
                            showDate = false,
                            onClick = { onEdit(expense) },
                        )
                    }
                }
            }
        }
    }

        SmallFloatingActionButton(
            onClick = onHistory,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.Rounded.History, contentDescription = "Historial")
        }
    }
}
