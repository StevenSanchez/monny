package cr.monny.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Envelope
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.ColorPicker
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.theme.PaletteHex
import cr.monny.ui.theme.hexToColor

/** Lista de sobres de ahorro con su saldo. */
@Composable
fun EnvelopesScreen(
    vm: MonnyViewModel,
    onOpen: (Long) -> Unit,
) {
    val envelopes by vm.envelopes.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val total = envelopes.sumOf { it.balanceCents }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sobres", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Apartás dinero para tus metas o para pagar la tarjeta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo", maxLines = 1, softWrap = false)
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "TOTAL EN SOBRES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Money.format(total), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${envelopes.size} ${if (envelopes.size == 1) "sobre" else "sobres"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (envelopes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Savings,
                    title = "Sin sobres todavía",
                    message = "Creá un sobre para separar plata: uno para la tarjeta, otro para un viaje, " +
                        "emergencias, lo que quieras.",
                    actionLabel = "Crear sobre",
                    onAction = { showCreate = true },
                )
            }
        }

        items(envelopes, key = { it.envelope.id }) { row ->
            val color = hexToColor(row.envelope.colorHex)
            MonnyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(row.envelope.id) },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(color.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(color, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.envelope.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sobre de ahorro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Money.format(row.balanceCents), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showCreate) {
        EnvelopeFormDialog(
            title = "Nuevo sobre",
            initial = null,
            onDismiss = { showCreate = false },
            onSave = { envelope ->
                vm.saveEnvelope(envelope) { showCreate = false }
            },
        )
    }
}

@Composable
fun EnvelopeFormDialog(
    title: String,
    initial: Envelope?,
    onDismiss: () -> Unit,
    onSave: (Envelope) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: PaletteHex[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Tarjeta BAC, Viaje, Emergencias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Envelope(name = "")).copy(
                            name = name.trim(),
                            colorHex = color,
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
