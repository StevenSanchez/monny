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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.Category
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.CategoryIcon
import cr.monny.ui.components.ColorPicker
import cr.monny.ui.components.ConfirmDialog
import cr.monny.ui.components.EmptyState
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.MoneyField
import cr.monny.ui.components.categoryIcon
import cr.monny.ui.theme.PaletteHex
import cr.monny.ui.theme.hexToColor

private data class Preset(val name: String, val icon: String, val color: String)

private val presets = listOf(
    Preset("Súper", "super", "#4E8C5A"),
    Preset("Comidas", "comida", "#C0603A"),
    Preset("Salidas", "salidas", "#A64B7A"),
    Preset("Combustible", "transporte", "#3A6B8C"),
    Preset("Peajes", "transporte", "#5B6B7A"),
    Preset("Salud", "salud", "#8C4E4E"),
    Preset("Hogar", "hogar", "#7A5CC4"),
    Preset("Mascotas", "mascotas", "#B5892F"),
    Preset("Ropa", "ropa", "#D9A441"),
    Preset("Servicios", "servicios", "#1F5C4B"),
)

private val iconKeys = listOf(
    "super", "comida", "salidas", "transporte", "salud",
    "hogar", "mascotas", "ropa", "servicios", "otros",
)

/** Categorías con color, ícono y presupuesto por ciclo. */
@Composable
fun CategoriesScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    Column(Modifier.fillMaxWidth()) {
        MonnyTopBar(
            title = "Categorías",
            subtitle = "${categories.size} en uso",
            onBack = onBack,
            actions = { TextButton(onClick = { creating = true }) { Text("Nueva") } },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (categories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Category,
                        title = "Sin categorías",
                        message = "Podés crearlas desde cero o empezar con las sugeridas.",
                    )
                }
                item {
                    SuggestedCard(
                        onAdd = { preset ->
                            vm.saveCategory(
                                Category(
                                    name = preset.name,
                                    colorHex = preset.color,
                                    iconKey = preset.icon,
                                    sortOrder = categories.size,
                                ),
                            )
                        },
                        onAddAll = {
                            presets.forEachIndexed { index, preset ->
                                vm.saveCategory(
                                    Category(
                                        name = preset.name,
                                        colorHex = preset.color,
                                        iconKey = preset.icon,
                                        sortOrder = index,
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            items(categories, key = { it.id }) { category ->
                val color = hexToColor(category.colorHex)
                MonnyCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = category },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            CategoryIcon(iconKey = category.iconKey, tint = color)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (category.budgetCents > 0) {
                                    "Presupuesto ${Money.format(category.budgetCents)} por ciclo"
                                } else {
                                    "Sin presupuesto"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = category }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { deleting = category }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val existing = categories.map { it.name.lowercase() }.toSet()
                            presets.filter { it.name.lowercase() !in existing }
                                .forEachIndexed { index, preset ->
                                    vm.saveCategory(
                                        Category(
                                            name = preset.name,
                                            colorHex = preset.color,
                                            iconKey = preset.icon,
                                            sortOrder = categories.size + index,
                                        ),
                                    )
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar categorías sugeridas")
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CategoryFormDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { category ->
                vm.saveCategory(category) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    deleting?.let { category ->
        ConfirmDialog(
            title = "Eliminar ${category.name}",
            message = "Solo se puede eliminar si no tiene gastos.",
            onConfirm = { vm.deleteCategory(category) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SuggestedCard(
    onAdd: (Preset) -> Unit,
    onAddAll: () -> Unit,
) {
    MonnyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sugeridas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Basadas en tus gastos habituales. Podés editarlas después.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdd(preset) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(hexToColor(preset.color).copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CategoryIcon(iconKey = preset.icon, tint = hexToColor(preset.color), size = 16.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(preset.name, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddAll, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar todas")
            }
        }
    }
}

@Composable
private fun CategoryFormDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: PaletteHex.first()) }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "otros") }
    var budget by remember {
        mutableStateOf(initial?.let { if (it.budgetCents > 0) (it.budgetCents / 100).toString() else "" } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva categoría" else "Editar categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Café, Mascotas, Regalos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                MoneyField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = "Presupuesto por ciclo (opcional)",
                )
                Spacer(Modifier.height(16.dp))
                Text("Ícono", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconKeys) { key ->
                        val selected = key == iconKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                categoryIcon(key),
                                contentDescription = key,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Category(name = "", colorHex = color)).copy(
                            name = name.trim(),
                            colorHex = color,
                            iconKey = iconKey,
                            budgetCents = Money.parse(budget) ?: 0L,
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
