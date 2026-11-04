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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Category
import cr.monny.data.InstallmentPlan
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.PlanRow
import cr.monny.ui.components.ConfirmDialog
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField

/** Lista de compras a tasa 0 con su progreso y edición. */
@Composable
fun PlansScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlanRow?>(null) }
    var deleting by remember { mutableStateOf<PlanRow?>(null) }

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Tasa 0",
            subtitle = "Compras en cuotas",
            onBack = onBack,
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (plans.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Percent,
                        title = "Sin compras a tasa 0",
                        message = "Cuando registres un gasto, activá \"Compra a tasa 0\" y elegí en cuántos " +
                            "meses se divide. Acá vas a poder ver y editar esas compras.",
                    )
                }
            }

            items(plans, key = { it.plan.id }) { row ->
                PlanCard(
                    row = row,
                    category = categories.firstOrNull { it.id == row.plan.categoryId },
                    onClick = { editing = row },
                )
            }
        }
    }

    editing?.let { row ->
        PlanFormDialog(
            plan = row.plan,
            categories = categories,
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                deleting = row
            },
            onSave = { updated ->
                vm.updatePlan(updated) { editing = null }
            },
        )
    }

    deleting?.let { row ->
        ConfirmDialog(
            title = "Eliminar ${row.plan.merchant}",
            message = "Se van a borrar las cuotas del ciclo actual en adelante. " +
                "Las cuotas de meses anteriores quedan en el historial.",
            onConfirm = { vm.deletePlan(row.plan) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlanCard(
    row: PlanRow,
    category: Category?,
    onClick: () -> Unit,
) {
    val months = row.plan.months
    val progress = row.charged.toFloat() / months

    MonnyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.plan.merchant.ifBlank { "Compra a tasa 0" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        category?.name ?: "Sin categoría",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.format(row.plan.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    row.remaining == 0 ->
                        "Terminada · $months cuotas de ${Money.format(row.plan.totalCents / months)}"
                    row.currentInstallment == months ->
                        "Cuota $months de $months · última · ${Money.format(row.plan.totalCents / months)}"
                    else ->
                        "Cuota ${row.currentInstallment} de $months · faltan " +
                            "${months - row.currentInstallment} después de esta · " +
                            "${Money.format(row.plan.totalCents / months)} por mes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanFormDialog(
    plan: InstallmentPlan,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (InstallmentPlan) -> Unit,
) {
    var merchant by remember { mutableStateOf(plan.merchant) }
    var total by remember { mutableStateOf((plan.totalCents / 100).toString()) }
    var monthsText by remember { mutableStateOf(plan.months.toString()) }
    var categoryId by remember { mutableStateOf(plan.categoryId) }

    val cents = Money.parse(total) ?: 0L
    val months = monthsText.toIntOrNull() ?: 0
    val valid = merchant.isNotBlank() && cents > 0 && months in 2..60 && categoryId != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar compra a tasa 0") },
        text = {
            Column {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyField(
                        value = total,
                        onValueChange = { total = it },
                        label = "Total",
                        modifier = Modifier.weight(1.4f),
                    )
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Cuotas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (cents > 0 && months in 2..60) {
                    Text(
                        "$months cuotas de ${Money.format(cents / months)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Si cambiás el monto o las cuotas se recalculan todas las cuotas de la compra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDelete) {
                    Text("Eliminar compra", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        plan.copy(
                            merchant = merchant.trim(),
                            totalCents = cents,
                            months = months,
                            categoryId = categoryId,
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
