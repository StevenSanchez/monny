package cr.monny.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Receipts
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.LocalImage
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField
import cr.monny.ui.theme.LocalMonnyColors
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
    val sharedPayments by vm.sharedPayments.collectAsStateWithLifecycle()
    val cycleKey = LocalDate.ofEpochDay(cycleKeyEpoch)
    val card = cards.firstOrNull { it.id == cardId }

    LaunchedEffect(cardId, cycleKeyEpoch) { vm.openCycle(cardId, cycleKey) }

    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var receiptPath by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }
    var confirmOverpay by remember { mutableStateOf(false) }
    var savedAmount by remember { mutableStateOf<Long?>(null) }
    var savedTotal by remember { mutableStateOf(0L) }
    var savedCovered by remember { mutableStateOf(0L) }
    var savedPaid by remember { mutableStateOf(0L) }
    var savedRemaining by remember { mutableStateOf(0L) }

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
    val statement = detail?.takeIf { it.card.id == cardId && it.cutKey == cycleKey }
    val sharedDebt = statement?.let { s ->
        (
            s.expenses.sumOf { it.amountCents * it.sharedPercent / 100 } -
                sharedPayments
                    .filter { it.cycleKey == s.cutKey.toEpochDay() }
                    .sumOf { it.amountCents }
            ).coerceAtLeast(0)
    } ?: 0L
    val myPart = (statement?.remainingCents ?: 0L) - sharedDebt
    val isOpenCycle = card != null && cycleKey.isAfter(CycleMath.lastCut(card.cutDay, LocalDate.now()))
    val remainingBefore = statement?.remainingCents ?: 0L
    val afterThisPayment = statement?.let { it.remainingCents - cents } ?: 0L

    /** Guarda el pago y deja una foto de los totales para el diálogo de éxito. */
    val registerPayment: () -> Unit = {
        statement?.let {
            savedTotal = it.totalCents
            savedCovered = it.coveredCents
            savedPaid = it.paidCents + cents
            savedRemaining = it.remainingCents - cents
        }
        vm.addPayment(
            cardId = cardId,
            cycleKey = cycleKey,
            date = date,
            amountCents = cents,
            note = note,
            receiptPath = receiptPath,
        )
        savedAmount = cents
    }

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
                        "CICLO ${CycleMath.monthLabel(CycleMath.cycleStart(cycleKey, card?.cutDay ?: 1)).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalMonnyColors.current.gold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${CycleMath.shortDate(CycleMath.cycleStart(cycleKey, card?.cutDay ?: 1))} – " +
                            "${CycleMath.shortDate(cycleKey)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Vence ${CycleMath.shortDate(CycleMath.paymentDue(cycleKey, card?.paymentDueDays ?: 14))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (statement != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            PaymentAmount(
                                label = "EN EL SOBRE",
                                cents = statement.coveredCents,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            PaymentAmount(
                                label = if (statement.remainingCents >= 0) {
                                    "FALTA POR PAGAR"
                                } else {
                                    "A FAVOR"
                                },
                                cents = kotlin.math.abs(statement.remainingCents),
                                modifier = Modifier.weight(1f),
                                color = if (statement.remainingCents < 0) {
                                    LocalMonnyColors.current.gold
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                horizontalAlignment = Alignment.End,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Gastos del ciclo ${Money.format(statement.totalCents)} · " +
                                "ya pagado ${Money.format(statement.paidCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sharedDebt > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when {
                                    myPart > 0 ->
                                        "Te deben ${Money.format(sharedDebt)} de este ciclo. " +
                                            "Tu parte del faltante: ${Money.format(myPart)}."
                                    myPart == 0L ->
                                        "Te deben ${Money.format(sharedDebt)}. Tu parte del " +
                                            "faltante ya está cubierta."
                                    else ->
                                        "Te deben ${Money.format(sharedDebt)}. Tu parte ya está " +
                                            "cubierta y quedan ${Money.format(-myPart)} a favor."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (isOpenCycle) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    "Este ciclo todavía no cierra (corte " +
                                        "${CycleMath.shortDate(cycleKey)}). Si pagás ahora, el " +
                                        "pago queda a favor de este ciclo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Cargando el estado de cuenta…",
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

            if (statement != null && remainingBefore > 0 && cents != remainingBefore) {
                TextButton(
                    onClick = { amount = Money.toDigits(remainingBefore) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pagar justo lo que falta (${Money.format(remainingBefore)})")
                }
            }

            if (statement != null && cents > 0) {
                val after = afterThisPayment
                val over = after < 0
                val exactly = after == 0L
                val accent = LocalMonnyColors.current
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        over -> accent.negativeContainer
                        exactly -> accent.positiveContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "DESPUÉS DE ESTE PAGO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                over -> "Quedaría ${Money.format(-after)} a favor"
                                exactly -> "El ciclo queda saldado"
                                else -> "Quedarían ${Money.format(after)} por pagar"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = when {
                                over -> accent.negative
                                exactly -> accent.positive
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                over ->
                                    "El ciclo necesita " +
                                        "${Money.format(remainingBefore.coerceAtLeast(0))} y estás " +
                                        "registrando ${Money.format(cents)}. Revisá el monto si " +
                                        "querés quedar justo."
                                exactly ->
                                    "Con este pago cubrís todo. En el sobre hay " +
                                        "${Money.format(statement.coveredCents)}."
                                else ->
                                    "En el sobre hay ${Money.format(statement.coveredCents)}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

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
                enabled = cents > 0 && card != null && statement != null,
                onClick = {
                    if (afterThisPayment < 0) {
                        confirmOverpay = true
                    } else {
                        registerPayment()
                    }
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

    if (confirmOverpay && statement != null) {
        val extra = (cents - remainingBefore).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { confirmOverpay = false },
            title = { Text("Quedaría a favor") },
            text = {
                Text(
                    "El ciclo necesita ${Money.format(remainingBefore.coerceAtLeast(0))} y " +
                        "estás registrando ${Money.format(cents)}.\n\n" +
                        "Quedarían ${Money.format(extra)} a favor del ciclo. " +
                        "Revisá el monto si querés quedar justo.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOverpay = false
                    registerPayment()
                }) { Text("Registrar igual") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOverpay = false }) { Text("Revisar monto") }
            },
        )
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

    savedAmount?.let { amount ->
        PaymentSuccessDialog(
            amountCents = amount,
            totalCents = savedTotal,
            coveredCents = savedCovered,
            paidCents = savedPaid,
            remainingCents = savedRemaining,
            cycleLabel = CycleMath.monthLabel(CycleMath.cycleStart(cycleKey, card?.cutDay ?: 1)),
            onDone = onBack,
        )
    }
}

@Composable
private fun PaymentAmount(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            Money.format(cents),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Confirmación grande después de registrar un pago, con el estado final del ciclo. */
@Composable
private fun PaymentSuccessDialog(
    amountCents: Long,
    totalCents: Long,
    coveredCents: Long,
    paidCents: Long,
    remainingCents: Long,
    cycleLabel: String,
    onDone: () -> Unit,
) {
    val accent = LocalMonnyColors.current
    val settled = remainingCents == 0L
    Dialog(onDismissRequest = onDone) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (settled) "¡Ciclo pagado!" else "Pago registrado",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        remainingCents < 0 ->
                            "Registramos ${Money.format(amountCents)} del ciclo de $cycleLabel. " +
                                "Quedó ${Money.format(-remainingCents)} a favor."
                        remainingCents == 0L ->
                            "Registramos ${Money.format(amountCents)} del ciclo de $cycleLabel. " +
                                "Quedó saldado."
                        else ->
                            "Registramos ${Money.format(amountCents)} del ciclo de $cycleLabel. " +
                                "Todavía faltan ${Money.format(remainingCents)}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DialogRow("Gastos del ciclo", totalCents)
                        DialogRow("En el sobre", coveredCents)
                        DialogRow("Pagado", paidCents)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DialogRow(
                            label = if (remainingCents >= 0) "Falta por pagar" else "A favor",
                            cents = kotlin.math.abs(remainingCents),
                            emphasize = true,
                            color = if (remainingCents < 0) accent.gold else accent.positive,
                        )
                    }
                }
                if (remainingCents < 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Si no era la idea, revisá el pago desde el historial.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Listo")
                }
            }
        }
    }
}

@Composable
private fun DialogRow(
    label: String,
    cents: Long,
    emphasize: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasize) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money.format(cents),
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = color,
        )
    }
}
