package cr.monny.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Expense
import cr.monny.data.Receipts
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.ColorPicker
import cr.monny.ui.components.LocalImage
import cr.monny.ui.components.MoneyField
import cr.monny.ui.components.groupDigits
import cr.monny.ui.theme.PaletteHex
import cr.monny.ui.theme.hexToColor
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Hoja inferior para crear o editar un gasto (también cuotas y compartidos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    vm: MonnyViewModel,
    editing: Expense?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories by vm.categories.collectAsStateWithLifecycle()
    val hints by vm.merchantHints.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val card = home.card
    val sharedEnabled by vm.sharedEnabled.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf(editing?.let { Money.toDigits(it.amountCents) } ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: 0L) }
    var merchant by remember { mutableStateOf(editing?.merchant ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var date by remember { mutableStateOf(editing?.let { LocalDate.ofEpochDay(it.date) } ?: LocalDate.now()) }
    var receiptPath by remember { mutableStateOf(editing?.receiptPath) }
    var showNote by remember { mutableStateOf(!editing?.note.isNullOrBlank()) }
    var showDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var creatingCategory by remember { mutableStateOf(false) }
    var isPlan by remember { mutableStateOf(false) }
    var monthsText by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf((editing?.sharedPercent ?: 0) > 0) }
    var sharedPercentText by remember {
        mutableStateOf(((editing?.sharedPercent ?: 0).takeIf { it > 0 } ?: 50).toString())
    }
    var cycleOverride by remember { mutableStateOf<LocalDate?>(null) }
    var showCyclePicker by remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        if (categoryId == 0L && categories.isNotEmpty()) categoryId = categories.first().id
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            Receipts.save(context, uri)?.let { receiptPath = it }
        }
    }

    val cents = Money.parse(amount) ?: 0L
    val canSave = card != null && cents > 0 && categoryId != 0L
    val derivedCycle = card?.let {
        CycleMath.purchaseCycleKey(date, it.cutDay, it.cutDayInNextCycle)
    }
    val effectiveCycle = cycleOverride ?: derivedCycle

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (editing == null) "Nuevo gasto" else "Editar gasto",
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }

            if (card == null) {
                Text(
                    "Primero creá una tarjeta en Ajustes → Tarjetas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Entendido") }
                return@Column
            }

            if (editing?.planId != null) {
                Text(
                    "Cuota ${editing.installmentIndex} de ${editing.installmentCount} de una compra a tasa 0. " +
                        "Para cambiar la compra completa, andá a Ajustes → Tasa 0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = "Monto",
                imeAction = ImeAction.Done,
            )

            Spacer(Modifier.height(16.dp))
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            if (categories.isEmpty() && !creatingCategory) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Todavía no hay categorías.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { creatingCategory = true }) { Text("Crear una") }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val selected = category.id == categoryId
                    FilterChip(
                        selected = selected,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(hexToColor(category.colorHex)),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { creatingCategory = true },
                    label = { Text("Nueva") },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }

            if (creatingCategory) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nombre de la categoría") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newCategoryName.isNotBlank(),
                        onClick = {
                            val name = newCategoryName.trim()
                            if (name.isNotEmpty()) {
                                vm.saveCategory(
                                    cr.monny.data.Category(
                                        name = name,
                                        colorHex = PaletteHex[categories.size % PaletteHex.size],
                                        iconKey = "otros",
                                        sortOrder = categories.size,
                                    ),
                                ) { newId ->
                                    categoryId = newId
                                }
                                newCategoryName = ""
                                creatingCategory = false
                            }
                        },
                    ) { Text("Crear") }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Motivo o comercio") },
                placeholder = { Text("Ej. Compass, Trigueros, Super") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            val suggestions = remember(merchant, hints) {
                val query = merchant.trim().lowercase()
                hints.filter { query.isEmpty() || it.lowercase().contains(query) }.take(6)
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        if (suggestion.lowercase() != merchant.trim().lowercase()) {
                            FilterChip(
                                selected = false,
                                onClick = { merchant = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CycleMath.longDate(date), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Ciclo ${effectiveCycle?.let { CycleMath.monthLabel(CycleMath.cycleStart(it, card.cutDay)) } ?: ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editing != null && effectiveCycle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCyclePicker = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mover a otro ciclo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (editing == null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Compra a tasa 0", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Divide el monto en cuotas mensuales",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isPlan, onCheckedChange = { isPlan = it })
                }
                if (isPlan) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Cantidad de cuotas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val months = monthsText.toIntOrNull()
                    if (months != null && months in 2..60 && cents > 0) {
                        Text(
                            "$months cuotas de ${Money.format(cents / months)} por mes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (sharedEnabled && !isPlan) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Compartido", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Otra persona te paga una parte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = shared, onCheckedChange = { shared = it })
                }
                if (shared) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sharedPercentText,
                        onValueChange = { input ->
                            sharedPercentText = input.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("Porcentaje de la otra persona") },
                        suffix = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val percent = sharedPercentText.toIntOrNull() ?: 0
                    if (percent in 1..100 && cents > 0) {
                        Text(
                            "Te corresponde ${Money.format(cents * (100 - percent) / 100)} · " +
                                "le corresponde ${Money.format(cents * percent / 100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!showNote) {
                TextButton(onClick = { showNote = true }) { Text("Agregar nota") }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
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
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (receiptPath == null) "Adjuntar comprobante" else "Cambiar comprobante")
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = canSave,
                onClick = {
                    val value = Money.parse(amount) ?: return@Button
                    if (categoryId == 0L) return@Button
                    val finalMerchant = merchant.trim()
                    val sharedValue = if (shared) {
                        (sharedPercentText.toIntOrNull() ?: 0).coerceIn(0, 100)
                    } else {
                        0
                    }
                    if (editing == null && isPlan) {
                        val months = monthsText.toIntOrNull() ?: return@Button
                        if (months !in 2..60) return@Button
                        vm.createPlan(
                            cardId = card.id,
                            categoryId = categoryId,
                            merchant = finalMerchant.ifBlank {
                                categories.firstOrNull { it.id == categoryId }?.name ?: "Compra"
                            },
                            totalCents = value,
                            months = months,
                            date = date,
                            note = note,
                        ) { onDismiss() }
                        return@Button
                    }
                    if (editing == null) {
                        vm.addExpense(
                            cardId = card.id,
                            categoryId = categoryId,
                            date = date,
                            amountCents = value,
                            merchant = finalMerchant,
                            note = note,
                            receiptPath = receiptPath,
                            sharedPercent = sharedValue,
                        )
                    } else {
                        vm.updateExpense(
                            editing.copy(
                                categoryId = categoryId,
                                date = date.toEpochDay(),
                                amountCents = value,
                                merchant = finalMerchant,
                                note = note,
                                receiptPath = receiptPath,
                                sharedPercent = sharedValue,
                            ),
                            cycleOverride = cycleOverride,
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (editing == null) "Guardar gasto" else "Guardar cambios", fontWeight = FontWeight.SemiBold)
            }

            if (editing != null) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Eliminar gasto", color = MaterialTheme.colorScheme.error)
                }
            }
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
                        cycleOverride = null
                    }
                    showDatePicker = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showDelete && editing != null) {
        cr.monny.ui.components.ConfirmDialog(
            title = "Eliminar gasto",
            message = "¿Seguro que querés borrar \"${editing.merchant}\"?",
            onConfirm = {
                vm.deleteExpense(editing)
                onDismiss()
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showCyclePicker && card != null && derivedCycle != null) {
        val baseCycle: LocalDate = derivedCycle!!
        val cutDay = card.cutDay
        val cycles = remember(baseCycle, cutDay) {
            buildList {
                add(baseCycle)
                var next = baseCycle
                repeat(4) {
                    next = CycleMath.nextCutKey(next, cutDay)
                    add(next)
                }
                var previous = baseCycle
                repeat(2) {
                    previous = CycleMath.previousCut(previous, cutDay)
                    add(previous)
                }
            }.distinct().sorted()
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCyclePicker = false },
            title = { Text("Elegir ciclo") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    cycles.forEach { key ->
                        val start = CycleMath.cycleStart(key, cutDay)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cycleOverride = key
                                    showCyclePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = key == effectiveCycle,
                                onClick = {
                                    cycleOverride = key
                                    showCyclePicker = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(CycleMath.monthLabel(start), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${CycleMath.shortDate(start)} – ${CycleMath.shortDate(key)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCyclePicker = false }) { Text("Cerrar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
