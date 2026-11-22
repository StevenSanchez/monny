package cr.monny.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Envelope
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.CategoryTotalRow
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.ExpenseRow
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MoneyField
import cr.monny.ui.components.Pill
import cr.monny.ui.components.SectionHeader
import cr.monny.ui.sheets.SharedPaymentSheet
import cr.monny.ui.theme.GoldSoft
import cr.monny.ui.theme.Green300
import cr.monny.ui.theme.Green700
import cr.monny.ui.theme.Green800
import cr.monny.ui.theme.Green900
import cr.monny.ui.theme.LocalMonnyColors
import java.time.LocalDate

/** Pantalla de inicio: resumen del ciclo actual, categorías y últimos gastos. */
@Composable
fun HomeScreen(
    vm: MonnyViewModel,
    onAddExpense: () -> Unit,
    onSeeAll: () -> Unit,
    onHistory: () -> Unit,
    onManageCards: () -> Unit,
    onPay: (Long, LocalDate) -> Unit,
    onAddEnvelope: () -> Unit,
    onImport: () -> Unit,
    onShared: () -> Unit,
    onEditExpense: (cr.monny.data.Expense) -> Unit,
) {
    val home by vm.home.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val sharedEnabled by vm.sharedEnabled.collectAsStateWithLifecycle()
    val sharedPayments by vm.sharedPayments.collectAsStateWithLifecycle()
    val pendingPayment by vm.pendingPayment.collectAsStateWithLifecycle()
    var showCover by remember { mutableStateOf(false) }
    var showSharedPayment by remember { mutableStateOf(false) }
    // Lo compartido se reinicia en cada corte: solo cuenta el ciclo abierto.
    val currentCycleKey = home.cutKey.toEpochDay()
    val sharedOwed = home.expenses.sumOf { it.amountCents * it.sharedPercent / 100 }
    val sharedPaid = sharedPayments
        .filter { it.cycleKey == currentCycleKey }
        .sumOf { it.amountCents }
    val sharedDebt = (sharedOwed - sharedPaid).coerceAtLeast(0)
    // Lo que realmente te toca poner: al faltante le restamos lo que te deben.
    val myRemaining = home.remainingCents - sharedDebt

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (home.card == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text("Monny", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tus tarjetas, tus sobres y tus gastos en un solo lugar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            EmptyState(
                icon = Icons.Rounded.CreditCard,
                title = "Empezá con tu tarjeta",
                message = "Agregá tu tarjeta de crédito, su fecha de corte y listo. " +
                    "Después creá tus categorías y empezá a registrar gastos.",
                actionLabel = "Crear tarjeta",
                onAction = onManageCards,
            )
            TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Ya tengo datos: importar o restaurar")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "MONNY",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalMonnyColors.current.gold,
                    )
                    Text(
                        CycleMath.monthLabel(home.cycleStart),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Tu ciclo actual",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (cards.size > 1) {
                    FilterChip(
                        selected = true,
                        onClick = onManageCards,
                        label = { Text(home.card?.name ?: "") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                        },
                    )
                }
            }
        }

        if (cards.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cards, key = { it.id }) { card ->
                        FilterChip(
                            selected = card.id == home.card?.id,
                            onClick = { vm.selectCard(card.id) },
                            label = { Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }

        item {
            HeroCard(
                home = home,
                sharedDebt = sharedDebt,
                myRemaining = myRemaining,
                onPay = {
                    home.card?.let { card ->
                        onPay(card.id, pendingPayment?.cutKey ?: home.cutKey)
                    }
                },
                onCover = { showCover = true },
                onHistory = onHistory,
            )
        }

        pendingPayment?.let { pending ->
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pago pendiente", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Ciclo ${CycleMath.monthLabel(pending.startKey)} · vence " +
                                        CycleMath.shortDate(pending.due),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "Falta ${Money.format(pending.remainingCents)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Gastado ${Money.format(pending.totalCents)} · " +
                                    "En sobre ${Money.format(pending.coveredCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    home.card?.let { card -> onPay(card.id, pending.cutKey) }
                                },
                            ) {
                                Text("Pagar")
                            }
                        }
                    }
                }
            }
        }

        if (sharedEnabled && sharedOwed > 0) {
            item {
                MonnyCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onShared() },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Compartido", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Gastos ${Money.format(sharedOwed)} · recibido ${Money.format(sharedPaid)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (sharedDebt > 0) "Te deben ${Money.format(sharedDebt)}" else "Al día",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sharedDebt > 0) {
                                TextButton(onClick = { showSharedPayment = true }) {
                                    Text("Registrar abono")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Ver compras",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        if (home.categoryTotals.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        SectionHeader("Por categoría") {
                            TextButton(onClick = onSeeAll) { Text("Ver gastos") }
                        }
                        Spacer(Modifier.height(4.dp))
                        home.categoryTotals.take(6).forEach { row ->
                            CategoryTotalRow(
                                name = row.category.name,
                                color = cr.monny.ui.theme.hexToColor(row.category.colorHex),
                                amountCents = row.totalCents,
                                share = if (home.totalCents > 0) row.totalCents.toFloat() / home.totalCents else 0f,
                                count = row.count,
                                budgetCents = row.category.budgetCents,
                            )
                        }
                    }
                }
            }
        }

        if (home.expenses.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        SectionHeader("Últimos gastos") {
                            TextButton(onClick = onSeeAll) { Text("Ver todos") }
                        }
                        home.expenses.take(5).forEach { expense ->
                            ExpenseRow(
                                expense = expense,
                                category = home.categoryOf(expense.categoryId),
                                onClick = { onEditExpense(expense) },
                            )
                        }
                    }
                }
            }
        } else {
            item {
                EmptyState(
                    icon = Icons.Rounded.Payments,
                    title = "Sin gastos todavía",
                    message = "Registrá el primer gasto de este ciclo con el botón de abajo.",
                    actionLabel = "Agregar gasto",
                    onAction = onAddExpense,
                )
            }
        }

        if (home.covers.isNotEmpty() || home.payments.isNotEmpty()) {
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        SectionHeader("Movimientos del ciclo")
                        home.payments.forEach { payment ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Pago · ${CycleMath.shortDate(LocalDate.ofEpochDay(payment.date))}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(Money.format(payment.amountCents), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        home.covers.forEach { cover ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    (if (cover.amountCents < 0) "Retiro · " else "Apartado · ") +
                                        CycleMath.shortDate(LocalDate.ofEpochDay(cover.date)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(Money.format(cover.amountCents), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCover && home.card != null) {
        CoverSheet(
            vm = vm,
            cardId = home.card!!.id,
            cycleKey = home.cutKey,
            coveredCents = home.coveredCents,
            onDismiss = { showCover = false },
        )
    }

    if (showSharedPayment && home.card != null && sharedDebt > 0) {
        SharedPaymentSheet(
            vm = vm,
            cardId = home.card!!.id,
            cycleKey = home.cutKey,
            debtCents = sharedDebt,
            onDismiss = { showSharedPayment = false },
        )
    }
}

/** Tarjeta grande con el resumen del ciclo y las acciones principales. */
@Composable
private fun HeroCard(
    home: cr.monny.ui.HomeState,
    sharedDebt: Long,
    myRemaining: Long,
    onPay: () -> Unit,
    onCover: () -> Unit,
    onHistory: () -> Unit,
) {
    val gold = LocalMonnyColors.current.gold
    val statusColor = when {
        home.totalCents == 0L -> Color.White.copy(alpha = 0.9f)
        home.remainingCents > 0 -> gold
        else -> Green300
    }
    val statusText = when {
        home.totalCents == 0L -> "Sin gastos"
        home.remainingCents > 0 -> "Falta ${Money.format(home.remainingCents)}"
        home.remainingCents < 0 -> "A favor ${Money.format(-home.remainingCents)}"
        else -> "Al día"
    }
    val progress by animateFloatAsState(
        targetValue = home.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "heroProgress",
    )
    val cutLabel = if (home.daysToCut == 0L) "hoy" else "en ${home.daysToCut} días"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Green900, spotColor = Green900)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Green900, Green800, Green700)))
            .drawBehind {
                // Halo dorado en la esquina superior derecha.
                val radius = size.width * 0.9f
                val center = Offset(size.width * 0.95f, 0f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(gold.copy(alpha = 0.14f), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            gold.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(gold, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "EN SOBRE · ${CycleMath.monthLabel(home.cycleStart).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = gold,
                    )
                }
                Pill(
                    text = statusText,
                    color = Color.White,
                    background = statusColor.copy(alpha = 0.18f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                Money.format(home.coveredCents),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "guardado para pagar ${Money.format(home.totalCents)} del ciclo",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(gold.copy(alpha = 0.8f), GoldSoft)),
                            CircleShape,
                        ),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Gastado", home.totalCents, Color.White, Modifier.weight(1f))
                HeroStat(
                    "Pagado",
                    home.paidCents,
                    Color.White.copy(alpha = 0.85f),
                    Modifier.weight(1f),
                    Alignment.CenterHorizontally,
                )
                HeroStat(
                    if (home.remainingCents >= 0) "Falta" else "A favor",
                    kotlin.math.abs(home.remainingCents),
                    if (home.remainingCents > 0) gold else Green300,
                    Modifier.weight(1f),
                    Alignment.End,
                )
            }

            if (sharedDebt > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (myRemaining >= 0) "TU PARTE DEL FALTANTE" else "A FAVOR TUYO",
                            style = MaterialTheme.typography.labelSmall,
                            color = gold,
                        )
                        Text(
                            "descontando ${Money.format(sharedDebt)} que te deben",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        Money.format(kotlin.math.abs(myRemaining)),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (myRemaining >= 0) Color.White else Green300,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                HeroMeta(
                    "CICLO",
                    "${CycleMath.shortDate(home.cycleStart)} – ${CycleMath.shortDate(home.cycleEnd)}",
                    Modifier.weight(1f),
                )
                HeroMeta("CORTE", cutLabel, Modifier.weight(0.7f), Alignment.CenterHorizontally)
                HeroMeta(
                    "PAGO",
                    CycleMath.shortDate(home.paymentDue),
                    Modifier.weight(0.7f),
                    Alignment.End,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCover,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Green800,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Savings,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Guardar en sobre",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Button(
                    onClick = onPay,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = gold,
                        contentColor = Green900,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Pagar", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.9f),
                ),
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Ver historial", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroMeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}


/** Hoja para apartar o retirar dinero del "en sobre" del ciclo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSheet(
    vm: MonnyViewModel,
    cardId: Long,
    cycleKey: LocalDate,
    coveredCents: Long,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val envelopes by vm.envelopes.collectAsStateWithLifecycle()
    var withdraw by remember { mutableStateOf(false) }
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
            Text(
                if (withdraw) "Retirar del sobre" else "Guardar en sobre",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (withdraw) {
                    "Este monto se descuenta de tu \"en sobre\" del ciclo que cierra el " +
                        "${CycleMath.shortDate(cycleKey)}. Después lo podés reponer con Guardar en sobre."
                } else {
                    "Este monto se suma a tu \"en sobre\" del ciclo que cierra el " +
                        "${CycleMath.shortDate(cycleKey)}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !withdraw,
                    onClick = { withdraw = false },
                    label = { Text("Guardar") },
                )
                FilterChip(
                    selected = withdraw,
                    onClick = { withdraw = true },
                    enabled = coveredCents > 0,
                    label = { Text("Retirar") },
                )
            }

            Spacer(Modifier.height(16.dp))
            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = if (withdraw) "Monto a retirar" else "Monto a guardar",
            )
            if (withdraw) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Disponible en sobre: ${Money.format(coveredCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!withdraw) {
                Spacer(Modifier.height(16.dp))
                Text("¿De dónde sale?", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = envelopeId == null,
                            onClick = { envelopeId = null },
                            label = { Text("De afuera") },
                        )
                    }
                    items(envelopes, key = { it.envelope.id }) { row ->
                        FilterChip(
                            selected = envelopeId == row.envelope.id,
                            onClick = { envelopeId = row.envelope.id },
                            enabled = row.balanceCents > 0,
                            label = {
                                Text("${row.envelope.name} · ${Money.format(row.balanceCents)}")
                            },
                        )
                    }
                }
                if (envelopes.isEmpty()) {
                    TextButton(onClick = onDismiss) {
                        Text("Crear sobres en la pestaña Sobres")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = if (withdraw) value in 1..coveredCents else value > 0,
                onClick = {
                    val parsed = Money.parse(amount) ?: return@Button
                    if (withdraw) {
                        vm.withdrawCover(cardId, cycleKey, parsed, note) { ok ->
                            if (ok) onDismiss()
                        }
                    } else {
                        vm.addCover(
                            cardId = cardId,
                            cycleKey = cycleKey,
                            amountCents = parsed,
                            envelopeId = envelopeId,
                            note = note,
                        ) { ok -> if (ok) onDismiss() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    when {
                        value <= 0L -> if (withdraw) "Retirar" else "Guardar en sobre"
                        withdraw -> "Retirar ${Money.format(value)}"
                        else -> "Guardar ${Money.format(value)}"
                    },
                )
            }
        }
    }
}
