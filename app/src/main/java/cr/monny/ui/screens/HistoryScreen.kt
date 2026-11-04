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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.CycleRow
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.Pill
import cr.monny.ui.theme.LocalMonnyColors

/** Historial de ciclos (incluye el próximo) con su estado de pago. */
@Composable
fun HistoryScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
    onOpen: (Long, java.time.LocalDate) -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Historial",
            subtitle = home.card?.name,
            onBack = onBack,
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "Nada por aquí",
                        message = "Cuando registres gastos vas a ver los ciclos anteriores.",
                    )
                }
            }
            items(history, key = { it.cutKey.toEpochDay() }) { row ->
                CycleHistoryCard(
                    row = row,
                    onClick = { home.card?.let { onOpen(it.id, row.cutKey) } },
                )
            }        }
    }
}

@Composable
private fun CycleHistoryCard(row: CycleRow, onClick: () -> Unit) {
    val extra = LocalMonnyColors.current
    MonnyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        CycleMath.monthLabel(row.startKey),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${row.count} ${if (row.count == 1) "gasto" else "gastos"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(row.totalCents), style = MaterialTheme.typography.titleMedium)
                    val future = row.startKey.isAfter(java.time.LocalDate.now())
                    if (future) {
                        Pill(
                            text = "Próximo ciclo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        Pill(
                            text = when {
                                row.remainingCents > 0 -> "Faltan ${Money.format(row.remainingCents)}"
                                row.remainingCents < 0 -> "A favor ${Money.format(-row.remainingCents)}"
                                else -> "Al día"
                            },
                            color = if (row.remainingCents > 0) extra.negative else extra.positive,
                            background = if (row.remainingCents > 0) extra.negativeContainer else extra.positiveContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "En sobre ${Money.format(row.coveredCents)} · Pagado ${Money.format(row.paidCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
