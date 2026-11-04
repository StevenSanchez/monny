package cr.monny.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Receipts
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.LocalImage
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
/** Formulario para registrar el pago de un ciclo, con comprobante opcional. */
@Composable
fun PaymentScreen(
    vm: MonnyViewModel,
    cardId: Long,
    cycleKeyEpoch: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val detail by vm.cycleDetail.collectAsStateWithLifecycle()
    val cycleKey = LocalDate.ofEpochDay(cycleKeyEpoch)
    val card = cards.firstOrNull { it.id == cardId }

    LaunchedEffect(cardId, cycleKeyEpoch) { vm.openCycle(cardId, cycleKey) }

    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var receiptPath by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(detail) {
        val d = detail
        if (!prefilled && d != null && d.card.id == cardId && d.cutKey == cycleKey) {
            if (d.remainingCents > 0) amount = Money.toDigits(d.remainingCents)
            prefilled = true
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) Receipts.save(context, uri)?.let { receiptPath = it }
    }

    val cents = Money.parse(amount) ?: 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        MonnyTopBar(
            title = "Registrar pago",
            subtitle = card?.name,
            onBack = onBack,
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Ciclo ${CycleMath.monthLabel(CycleMath.cycleStart(cycleKey, card?.cutDay ?: 1))}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${CycleMath.shortDate(CycleMath.cycleStart(cycleKey, card?.cutDay ?: 1))} – " +
                            "${CycleMath.shortDate(cycleKey)} · vence " +
                            CycleMath.shortDate(CycleMath.paymentDue(cycleKey, card?.paymentDueDays ?: 14)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val d = detail
                    if (d != null && d.cutKey == cycleKey) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Gastado ${Money.format(d.totalCents)} · " +
                                "En sobre ${Money.format(d.coveredCents)} · " +
                                "Falta ${Money.format(d.remainingCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = "Monto pagado",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Fecha del pago",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(CycleMath.longDate(date), style = MaterialTheme.typography.bodyLarge)
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (receiptPath != null) {
                    LocalImage(path = receiptPath!!,
                        contentDescription = "Comprobante",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(
                        if (receiptPath == null) Icons.Rounded.PhotoCamera else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (receiptPath == null) "Adjuntar comprobante" else "Quitar comprobante")
                }
            }

            Button(
                enabled = cents > 0 && card != null,
                onClick = {
                    vm.addPayment(
                        cardId = cardId,
                        cycleKey = cycleKey,
                        date = date,
                        amountCents = cents,
                        note = note,
                        receiptPath = receiptPath,
                    )
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    if (cents > 0) "Registrar ${Money.format(cents)}" else "Registrar pago",
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = state) }
    }
}
