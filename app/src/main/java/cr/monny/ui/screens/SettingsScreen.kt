package cr.monny.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.ThemeMode
import cr.monny.notifications.Reminders
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.MonnyCard
import kotlinx.coroutines.launch

/** Ajustes: apariencia, accesos, recordatorios y gastos compartidos. */
@Composable
fun SettingsScreen(
    vm: MonnyViewModel,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onCards: () -> Unit,
    onCategories: () -> Unit,
    onHistory: () -> Unit,
    onData: () -> Unit,
    onPlans: () -> Unit,
    onReports: () -> Unit,
    onRecurring: () -> Unit,
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val shared by vm.sharedEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Todo se guarda en tu teléfono. Sin cuentas ni servidores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(
                            label = "Sistema",
                            icon = Icons.Rounded.SettingsBrightness,
                            selected = theme == ThemeMode.SYSTEM,
                            onClick = { onThemeChange(ThemeMode.SYSTEM) },
                        )
                        ThemeChip(
                            label = "Claro",
                            icon = Icons.Rounded.LightMode,
                            selected = theme == ThemeMode.LIGHT,
                            onClick = { onThemeChange(ThemeMode.LIGHT) },
                        )
                        ThemeChip(
                            label = "Oscuro",
                            icon = Icons.Rounded.DarkMode,
                            selected = theme == ThemeMode.DARK,
                            onClick = { onThemeChange(ThemeMode.DARK) },
                        )
                    }
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    SettingsRow(
                        icon = Icons.Rounded.CreditCard,
                        title = "Tarjetas",
                        subtitle = "${cards.size} ${if (cards.size == 1) "tarjeta" else "tarjetas"}",
                        onClick = onCards,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Category,
                        title = "Categorías",
                        subtitle = "Organizá tus gastos",
                        onClick = onCategories,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.History,
                        title = "Historial",
                        subtitle = "Ciclos anteriores",
                        onClick = onHistory,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.ImportExport,
                        title = "Datos",
                        subtitle = "Respaldar, importar y exportar CSV",
                        onClick = onData,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Percent,
                        title = "Tasa 0",
                        subtitle = "Compras en cuotas",
                        onClick = onPlans,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "Reportes",
                        subtitle = "Gastos por ciclo y categoría",
                        onClick = onReports,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Gastos fijos",
                        subtitle = "Seguros, suscripciones y cobros mensuales",
                        onClick = onRecurring,
                    )
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los avisos de corte y de pago se configuran por tarjeta: " +
                            "elegís los días de anticipación y si querés el aviso del día de pago.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        Reminders.schedule(context)
                        scope.launch { }
                    }) { Text("Reactivar recordatorios") }
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Gastos compartidos", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Si comprás para otra persona con tu tarjeta y te paga una parte, activá esto. " +
                            "Al registrar un gasto vas a poder marcar qué porcentaje le corresponde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (shared) "Activado" else "Desactivado",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = shared, onCheckedChange = { vm.setSharedEnabled(it) })
                    }
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Monny 0.1.0", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Hecho para llevar las cuentas de la tarjeta sin complicaciones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
