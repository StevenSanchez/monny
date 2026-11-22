package cr.monny.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.MoneyField
import java.time.LocalDate

/**
 * Hoja para registrar un abono de gastos compartidos.
 *
 * El monto baja la deuda y entra al "en sobre" del ciclo actual o al sobre
 * que elija el usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPaymentSheet(
    vm: MonnyViewModel,
    cardId: Long,
    cycleKey: LocalDate,
    debtCents: Long,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val envelopes by vm.envelopes.collectAsStateWithLifecycle()
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var envelopeId by remember { mutableStateOf<Long?>(null) }
    val value = Money.parse(amount) ?: 0L

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Registrar abono", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Te deben ${Money.format(debtCents)}. El monto se descuenta de esa deuda " +
                    "y se suma al destino que elijas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            MoneyField(value = amount, onValueChange = { amount = it }, label = "Monto recibido")

            Spacer(Modifier.height(16.dp))
            Text("¿A dónde va la plata?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = envelopeId == null,
                        onClick = { envelopeId = null },
                        label = { Text("En sobre") },
                    )
                }
                items(envelopes, key = { it.envelope.id }) { row ->
                    FilterChip(
                        selected = envelopeId == row.envelope.id,
                        onClick = { envelopeId = row.envelope.id },
                        label = { Text(row.envelope.name) },
                    )
                }
            }
            Text(
                if (envelopeId == null) {
                    "Se suma al apartado del ciclo actual."
                } else {
                    "Se suma al saldo del sobre " +
                        (envelopes.firstOrNull { it.envelope.id == envelopeId }?.envelope?.name ?: "") + "."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = value in 1..debtCents,
                onClick = {
                    val parsed = Money.parse(amount) ?: return@Button
                    vm.addSharedPayment(
                        cardId = cardId,
                        cycleKey = cycleKey,
                        amountCents = parsed,
                        envelopeId = envelopeId,
                        note = note,
                    ) { ok -> if (ok) onDismiss() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    if (value > 0) "Registrar ${Money.format(value)}" else "Registrar abono",
                )
            }
        }
    }
}
