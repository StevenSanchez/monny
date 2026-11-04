package cr.monny.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Card
import cr.monny.domain.CycleMath
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.ColorPicker
import cr.monny.ui.components.ConfirmDialog
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.theme.PaletteHex
import cr.monny.ui.theme.hexToColor

/** Administración de tarjetas: corte, pago y recordatorios. */
@Composable
fun CardsScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Card?>(null) }
    var deleting by remember { mutableStateOf<Card?>(null) }

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Tarjetas",
            subtitle = "Fecha de corte y de pago",
            onBack = onBack,
            actions = {
                TextButton(onClick = { creating = true }) { Text("Nueva") }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (cards.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CreditCard,
                        title = "Sin tarjetas",
                        message = "Agregá tu primera tarjeta para empezar a controlar los ciclos.",
                        actionLabel = "Agregar tarjeta",
                        onAction = { creating = true },
                    )
                }
            }
            items(cards, key = { it.id }) { card ->
                val color = hexToColor(card.colorHex)
                MonnyCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = color)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    buildString {
                                        if (card.bank.isNotBlank()) append(card.bank)
                                        if (card.last4.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append("•••• ")
                                            append(card.last4)
                                        }
                                    }.ifBlank { "Tarjeta de crédito" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = card }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { deleting = card }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Eliminar")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Corte el día ${card.cutDay} · Pago ${card.paymentDueDays} días después",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Próximo corte: ${CycleMath.shortDate(CycleMath.nextCut(card.cutDay))} · " +
                                "Pago: ${CycleMath.shortDate(CycleMath.paymentDue(CycleMath.nextCut(card.cutDay), card.paymentDueDays))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CardFormDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { card ->
                vm.saveCard(card) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    deleting?.let { card ->
        ConfirmDialog(
            title = "Eliminar ${card.name}",
            message = "Se van a borrar también los gastos, pagos y apartados de esta tarjeta. " +
                "Esta acción no se puede deshacer.",
            onConfirm = { vm.deleteCard(card) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CardFormDialog(
    initial: Card?,
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var bank by remember { mutableStateOf(initial?.bank ?: "") }
    var last4 by remember { mutableStateOf(initial?.last4 ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: PaletteHex.first()) }
    var cutDay by remember { mutableStateOf((initial?.cutDay ?: 1).toString()) }
    var dueDays by remember { mutableStateOf((initial?.paymentDueDays ?: 14).toString()) }
    var notifyCut by remember { mutableStateOf(initial?.notifyCut ?: true) }
    var notifyDays by remember { mutableStateOf((initial?.notifyDaysBeforeCut ?: 3).toString()) }
    var notifyPayment by remember { mutableStateOf(initial?.notifyPayment ?: true) }
    var cutNextCycle by remember { mutableStateOf(initial?.cutDayInNextCycle ?: true) }

    val cut = cutDay.toIntOrNull() ?: 0
    val due = dueDays.toIntOrNull() ?: 0
    val before = notifyDays.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && cut in 1..31 && due in 0..60 && before in 0..30

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva tarjeta" else "Editar tarjeta") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. BAC Pricesmart") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text("Banco (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = last4,
                    onValueChange = { last4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Últimos 4 dígitos (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = cutDay,
                        onValueChange = { cutDay = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Día de corte") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = cut !in 1..31,
                    )
                    OutlinedTextField(
                        value = dueDays,
                        onValueChange = { dueDays = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Días para pagar") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = due !in 0..60,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ColorPicker(selected = color, onSelect = { color = it })
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aviso de corte", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Notificar antes del corte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = notifyCut, onCheckedChange = { notifyCut = it })
                }
                if (notifyCut) {
                    OutlinedTextField(
                        value = notifyDays,
                        onValueChange = { notifyDays = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Días antes del corte") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = before !in 0..30,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aviso de pago", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Notificar el día del vencimiento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = notifyPayment, onCheckedChange = { notifyPayment = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compras del día de corte", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Contarlas en el siguiente ciclo (el banco ya cerró)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = cutNextCycle, onCheckedChange = { cutNextCycle = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        (initial ?: Card(name = "")).copy(
                            name = name.trim(),
                            bank = bank.trim(),
                            last4 = last4.trim(),
                            colorHex = color,
                            cutDay = cut,
                            paymentDueDays = due,
                            notifyCut = notifyCut,
                            notifyDaysBeforeCut = before,
                            notifyPayment = notifyPayment,
                            cutDayInNextCycle = cutNextCycle,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )
}
