package cr.monny.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.CategoryTotalRow
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.KeyValueRow
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.theme.hexToColor

/** Reportes por ciclo: resumen, gráfico de barras y desglose por categoría. */
@Composable
fun ReportsScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val expenses by vm.allExpenses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val sharedEnabled by vm.sharedEnabled.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf(6) }

    val rows = remember(history, period) { history.take(period).reversed() }
    val cutKeys = remember(rows) { rows.map { it.cutKey.toEpochDay() }.toSet() }
    val periodExpenses = remember(expenses, cutKeys) { expenses.filter { it.cycleKey in cutKeys } }

    val total = rows.sumOf { it.totalCents }
    val average = if (rows.isEmpty()) 0L else total / rows.size
    val maximum = rows.maxOfOrNull { it.totalCents } ?: 0L
    val sharedOwed = periodExpenses.sumOf { it.amountCents * it.sharedPercent / 100 }

    val byCategory = remember(periodExpenses, categories) {
        periodExpenses.groupBy { it.categoryId }
            .map { (id, list) ->
                Triple(categories.firstOrNull { it.id == id }, list.sumOf { it.amountCents }, list.size)
            }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MonnyTopBar(
                title = "Reportes",
                subtitle = "Tus últimos ciclos",
                onBack = onBack,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = period == 6,
                    onClick = { period = 6 },
                    label = { Text("6 ciclos") },
                )
                FilterChip(
                    selected = period == 12,
                    onClick = { period = 12 },
                    label = { Text("12 ciclos") },
                )
            }
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Sin datos todavía",
                    message = "Cuando registres gastos vas a ver acá los totales por ciclo y por categoría.",
                )
            }
        } else {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Resumen", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("Total del período", Money.format(total), emphasis = true)
                        KeyValueRow("Promedio por ciclo", Money.format(average))
                        KeyValueRow("Ciclo más alto", Money.format(maximum))
                        if (sharedEnabled && sharedOwed > 0) {
                            KeyValueRow("Te deben (compartido)", Money.format(sharedOwed))
                        }
                    }
                }
            }

            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Gasto por ciclo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        CycleBars(
                            values = rows.map { it.totalCents },
                            labels = rows.map { CycleMath.monthLabel(it.startKey).take(3) },
                            max = maximum,
                        )
                    }
                }
            }

            if (byCategory.isNotEmpty()) {
                item {
                    MonnyCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Por categoría", style = MaterialTheme.typography.titleMedium)
                            byCategory.forEach { (category, amount, count) ->
                                CategoryTotalRow(
                                    name = category?.name ?: "Sin categoría",
                                    color = hexToColor(category?.colorHex ?: "#8A8F8B"),
                                    amountCents = amount,
                                    share = if (total > 0) amount.toFloat() / total else 0f,
                                    count = count,
                                    budgetCents = 0,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleBars(
    values: List<Long>,
    labels: List<String>,
    max: Long,
) {
    val scroll = rememberScrollState()
    val chartHeight = 140.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val fraction = if (max > 0) value.toFloat() / max else 0f
            Column(
                modifier = Modifier.width(38.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((chartHeight * fraction).coerceAtLeast(3.dp))
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
