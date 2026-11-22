package cr.monny.ui.screens

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
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.sheets.SharedPaymentSheet
import java.time.LocalDate

/**
 * Detalle de gastos compartidos: qué compras son, cuánto te deben y los abonos
 * recibidos. También explica por qué el gasto completo va a la tarjeta.
 */
@Composable
fun SharedScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val allExpenses by vm.allExpenses.collectAsStateWithLifecycle()
    val sharedPayments by vm.sharedPayments.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val envelopes by vm.envelopes.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()

    // El compartido se reinicia con cada corte: solo se muestra el ciclo abierto.
    val cycleKey = home.cutKey.toEpochDay()
    val sharedExpenses = remember(allExpenses, cycleKey) {
        allExpenses
            .filter { it.sharedPercent > 0 && it.cycleKey == cycleKey }
            .sortedByDescending { it.date }
    }
    val owed = sharedExpenses.sumOf { it.amountCents * it.sharedPercent / 100 }
    val paid = sharedPayments.filter { it.cycleKey == cycleKey }.sumOf { it.amountCents }
    val cyclePayments = remember(sharedPayments, cycleKey) {
        sharedPayments.filter { it.cycleKey == cycleKey }
    }
    val debt = owed - paid

    var showAbono by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Compartido",
            subtitle = "Ciclo ${CycleMath.monthLabel(home.cycleStart)}",
            onBack = onBack,
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "TE DEBEN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Money.format(debt.coerceAtLeast(0)),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (debt > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Compras ${Money.format(owed)} · recibido ${Money.format(paid)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (debt > 0 && home.card != null) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showAbono = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Registrar abono") }
                        }
                    }
                }
            }

            item {
                Text(
                    "Cada compra se cobra completa en tu tarjeta; lo que te deben es la parte " +
                        "de la otra persona. Esta cuenta se reinicia en cada corte: cuando te " +
                        "pagan, registrás el abono y esa plata se guarda en el \"en sobre\" o en " +
                        "un sobre para cubrir la tarjeta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Text("Compras de este ciclo", style = MaterialTheme.typography.titleMedium)
            }

            if (sharedExpenses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.People,
                        title = "Sin compras compartidas",
                        message = "En este ciclo no hay compras compartidas. Cuando registres un " +
                            "gasto, activá \"Compartido\" e indicá qué porcentaje le corresponde " +
                            "a la otra persona.",
                    )
                }
            }

            items(sharedExpenses, key = { it.id }) { expense ->
                val category = categories.firstOrNull { it.id == expense.categoryId }
                val owedByOther = expense.amountCents * expense.sharedPercent / 100
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    expense.merchant.ifBlank { "Gasto" },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    (category?.name ?: "Sin categoría") + " · " +
                                        CycleMath.shortDate(LocalDate.ofEpochDay(expense.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                Money.format(expense.amountCents),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Le corresponde ${expense.sharedPercent}%: ${Money.format(owedByOther)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (cyclePayments.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Abonos recibidos", style = MaterialTheme.typography.titleMedium)
                }
                items(cyclePayments, key = { it.id }) { payment ->
                    val destination = payment.envelopeId?.let { id ->
                        envelopes.firstOrNull { it.envelope.id == id }?.envelope?.name
                    } ?: "En sobre"
                    MonnyCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(destination, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    CycleMath.shortDate(LocalDate.ofEpochDay(payment.date)) +
                                        payment.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "+${Money.format(payment.amountCents)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAbono && home.card != null) {
        SharedPaymentSheet(
            vm = vm,
            cardId = home.card!!.id,
            cycleKey = home.cutKey,
            debtCents = debt,
            onDismiss = { showAbono = false },
        )
    }
}
