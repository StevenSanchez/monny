package cr.monny.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import cr.monny.ui.components.MoneyField
import cr.monny.ui.theme.LocalMonnyColors
import cr.monny.ui.theme.hexToColor
import java.time.LocalDate

/** Detalle de un sobre: saldo, aportar, retirar y pasar dinero a la tarjeta. */
@Composable
fun EnvelopeDetailScreen(
    vm: MonnyViewModel,
    envelopeId: Long,
    onBack: () -> Unit,
) {
    val envelopes by vm.envelopes.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val txs by remember(envelopeId) { vm.observeEnvelopeTxs(envelopeId) }
        .collectAsStateWithLifecycle()
    val row = envelopes.firstOrNull { it.envelope.id == envelopeId }

    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDeposit by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showToCard by remember { mutableStateOf(false) }
    var showTxSheet by remember { mutableStateOf(false) }

    if (row == null) {
        Column {
            MonnyTopBar("Sobre", onBack = onBack)
            EmptyState(
                icon = Icons.Rounded.ArrowDownward,
                title = "Sobre no encontrado",
                message = "Puede que lo hayas eliminado.",
                actionLabel = "Volver",
                onAction = onBack,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonnyTopBar(
                title = row.envelope.name,
                subtitle = "Sobre de ahorro",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Editar sobre") },
                                onClick = { menuOpen = false; showEdit = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar sobre") },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "SALDO ACTUAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(row.balanceCents),
                        style = MaterialTheme.typography.displaySmall,
                        color = hexToColor(row.envelope.colorHex),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showDeposit = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Aportar") }
                        FilledTonalButton(
                            onClick = { showWithdraw = true },
                            modifier = Modifier.weight(1f),
                            enabled = row.balanceCents > 0,
                        ) { Text("Retirar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showToCard = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cards.isNotEmpty() && row.balanceCents > 0,
                    ) {
                        Icon(Icons.Rounded.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Usar para la tarjeta")
                    }
                }
            }
        }

        item {
            Text("Movimientos", style = MaterialTheme.typography.titleMedium)
        }

        if (txs.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ArrowDownward,
                    title = "Sin movimientos",
                    message = "Cuando aportes o retires dinero va a aparecer aquí.",
                )
            }
        }

        items(txs, key = { it.id }) { tx ->
            val positive = tx.amountCents >= 0
            MonnyCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (positive) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = if (positive) LocalMonnyColors.current.positive else LocalMonnyColors.current.negative,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tx.note.ifBlank { if (positive) "Aporte" else "Retiro" })
                        Text(
                            CycleMath.shortDate(LocalDate.ofEpochDay(tx.date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (positive) "+" else "") + Money.format(tx.amountCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (positive) LocalMonnyColors.current.positive else LocalMonnyColors.current.negative,
                    )
                }
            }
        }
    }

    if (showEdit) {
        EnvelopeFormDialog(
            title = "Editar sobre",
            initial = row.envelope,
            onDismiss = { showEdit = false },
            onSave = { envelope -> vm.saveEnvelope(envelope) { showEdit = false } },
        )
    }

    if (showDelete) {
        cr.monny.ui.components.ConfirmDialog(
            title = "Eliminar sobre",
            message = "Se va a borrar \"${row.envelope.name}\". Solo se puede si no tiene movimientos.",
            onConfirm = { vm.deleteEnvelope(row.envelope) },
            onDismiss = { showDelete = false },
        )
    }

    if (showDeposit) {
        AmountDialog(
            title = "Aportar a ${row.envelope.name}",
            confirmLabel = "Aportar",
            onDismiss = { showDeposit = false },
            onConfirm = { cents, note ->
                vm.moveEnvelopeMoney(row.envelope.id, cents, true, note) { ok ->
                    if (ok) showDeposit = false
                }
            },
        )
    }

    if (showWithdraw) {
        AmountDialog(
            title = "Retirar de ${row.envelope.name}",
            confirmLabel = "Retirar",
            maxCents = row.balanceCents,
            onDismiss = { showWithdraw = false },
            onConfirm = { cents, note ->
                vm.moveEnvelopeMoney(row.envelope.id, cents, false, note) { ok ->
                    if (ok) showWithdraw = false
                }
            },
        )
    }

    if (showToCard) {
        UseInCardSheet(
            vm = vm,
            envelopeId = row.envelope.id,
            balanceCents = row.balanceCents,
            onDismiss = { showToCard = false },
        )
    }
}

@Composable
private fun AmountDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
    maxCents: Long? = null,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val value = Money.parse(amount) ?: 0L
    val valid = value > 0 && (maxCents == null || value <= maxCents)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Monto")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (maxCents != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Disponible: ${Money.format(maxCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onConfirm(value, note) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UseInCardSheet(
    vm: MonnyViewModel,
    envelopeId: Long,
    balanceCents: Long,
    onDismiss: () -> Unit,
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cardId by remember { mutableStateOf(cards.firstOrNull()?.id ?: 0L) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Usar para la tarjeta", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pasá dinero del sobre al \"en sobre\" del ciclo actual de la tarjeta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            MoneyField(value = amount, onValueChange = { amount = it }, label = "Monto")
            Spacer(Modifier.height(12.dp))
            Text("Tarjeta", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards, key = { it.id }) { card ->
                    FilterChip(
                        selected = card.id == cardId,
                        onClick = { cardId = card.id },
                        label = { Text(card.name) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (cards.isNotEmpty()) {
                val card = cards.firstOrNull { it.id == cardId } ?: cards.first()
                Text(
                    "Se va a apartar para el corte del ${CycleMath.shortDate(CycleMath.nextCut(card.cutDay))}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            val value = Money.parse(amount) ?: 0L
            Button(
                enabled = value in 1..balanceCents && cardId != 0L,
                onClick = {
                    val card = cards.firstOrNull { it.id == cardId } ?: return@Button
                    vm.addCover(
                        cardId = card.id,
                        cycleKey = CycleMath.nextCut(card.cutDay),
                        amountCents = value,
                        envelopeId = envelopeId,
                        note = note,
                    ) { ok -> if (ok) onDismiss() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Mover ${Money.format(value)}") }
        }
    }
}
