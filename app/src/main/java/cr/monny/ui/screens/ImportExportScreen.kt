package cr.monny.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.domain.CsvImport
import cr.monny.domain.CsvParseResult
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.components.MonnyCard
import cr.monny.ui.components.MonnyTopBar
import cr.monny.ui.components.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Respaldos (JSON/CSV) e importación desde CSV o Google Sheets. */
@Composable
fun ImportExportScreen(
    vm: MonnyViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cards by vm.cards.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("") }
    var sheetName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<CsvParseResult?>(null) }
    var selectedCardId by remember { mutableStateOf(0L) }
    var defaultCategory by remember { mutableStateOf("") }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var showWipe by remember { mutableStateOf(false) }
    var includeCover by remember(parsed) { mutableStateOf(true) }
    var includePayments by remember(parsed) { mutableStateOf(true) }

    LaunchedEffect(cards) {
        if (selectedCardId == 0L) selectedCardId = cards.firstOrNull()?.id ?: 0L
    }

    fun parseText(text: String) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                CsvImport.detect(CsvImport.parse(text))
            }
            loading = false
            if (result.rows.isEmpty()) {
                parsed = null
                error = if (text.trimStart().startsWith("<")) {
                    "El enlace devolvió una página web. Verificá que la hoja sea pública " +
                        "o usá el enlace de exportación CSV."
                } else {
                    "No pude encontrar gastos en ese archivo. Revisá que tenga columnas de fecha y monto."
                }
            } else {
                parsed = result
            }
        }
    }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    }.getOrNull()
                }
                if (text != null) parseText(text) else error = "No pude leer el archivo"
            }
        }
    }

    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    }.getOrNull()
                }
                if (text != null) pendingJson = text else error = "No pude leer el respaldo"
            }
        }
    }

    fun shareFile(fileName: String, mime: String, content: String) {
        scope.launch {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            withContext(Dispatchers.IO) { file.writeText(content) }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir"))
        }
    }

    fun exportBackup() {
        vm.exportBackup { json ->
            shareFile("monny-${LocalDate.now()}.json", "application/json", json)
        }
    }

    fun exportCsv() {
        vm.exportExpensesCsv { csv ->
            shareFile("monny-gastos-${LocalDate.now()}.csv", "text/csv", csv)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MonnyTopBar(
                title = "Datos",
                subtitle = "Respaldos e importación",
                onBack = onBack,
            )
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Respaldo completo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Exportá todo (tarjetas, gastos, sobres y presupuestos) a un archivo JSON. " +
                            "Sirve para cambiar de teléfono o guardar una copia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportBackup() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exportar")
                        }
                        OutlinedButton(
                            onClick = { openJson.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restaurar")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Restaurar reemplaza todos los datos actuales por los del archivo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Además se guarda un respaldo automático por día en Descargas/Monny " +
                            "(se conservan los últimos 5).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Exportar gastos (CSV)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Un archivo con fecha, motivo, monto, categoría, tarjeta y ciclo. " +
                            "Se abre en Excel, Numbers o Google Sheets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { exportCsv() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir CSV de gastos")
                    }
                }
            }
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Importar gastos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Desde un CSV o desde una hoja de Google pública. Reconoce hojas con " +
                            "bloques por categoría (Fecha / Motivo / Monto) y tablas planas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            label = { Text("Enlace") },
                            leadingIcon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        FilterChip(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            label = { Text("Archivo") },
                            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }

                    if (tab == 0) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Enlace de Google Sheets o CSV") },
                            placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sheetName,
                            onValueChange = { sheetName = it },
                            label = { Text("Hoja (opcional, ej. Octubre)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = url.isNotBlank() && !loading,
                            onClick = {
                                loading = true
                                error = null
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        runCatching { fetchCsv(url, sheetName) }.getOrNull()
                                    }
                                    loading = false
                                    if (text == null) {
                                        error = "No pude descargar el enlace. Verificá que sea público y tenga conexión."
                                    } else {
                                        parseText(text)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Leer datos")
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Elegir archivo CSV")
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        parsed?.let { result ->
            item {
                MonnyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Vista previa", style = MaterialTheme.typography.titleMedium)
                            Pill(
                                text = if (result.detectedFormat == "bloques") "Bloques por categoría" else "Tabla",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                background = MaterialTheme.colorScheme.primaryContainer,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${result.rows.size} gastos · ${Money.format(result.totalCents)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (result.dateFrom != null && result.dateTo != null) {
                            Text(
                                "${CycleMath.shortDate(result.dateFrom)} – ${CycleMath.shortDate(result.dateTo)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.skipped > 0) {
                            Text(
                                "${result.skipped} filas sin fecha o monto válido",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.undated > 0) {
                            Text(
                                "${result.undated} gastos sin fecha se asignan al ciclo principal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.categories.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Categorías: ${result.categories.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        result.enSobreCents?.let { cover ->
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("En sobre de la hoja", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        Money.format(cover),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Switch(
                                    checked = includeCover,
                                    onCheckedChange = { includeCover = it },
                                )
                            }
                        }

                        if (result.payments.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Pagos detectados (${result.payments.size})",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        Money.format(result.paymentsCents),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Switch(
                                    checked = includePayments,
                                    onCheckedChange = { includePayments = it },
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("Tarjeta destino", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(cards, key = { it.id }) { card ->
                                FilterChip(
                                    selected = card.id == selectedCardId,
                                    onClick = { selectedCardId = card.id },
                                    label = { Text(card.name) },
                                )
                            }
                        }

                        if (result.categories.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = defaultCategory,
                                onValueChange = { defaultCategory = it },
                                label = { Text("Categoría para estos gastos") },
                                placeholder = { Text("Ej. Importado") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            enabled = selectedCardId != 0L,
                            onClick = {
                                val cardId = selectedCardId
                                vm.importExpenses(
                                    cardId = cardId,
                                    items = result.rows,
                                    payments = if (includePayments) result.payments else emptyList(),
                                    coverCents = if (includeCover) result.enSobreCents ?: 0L else 0L,
                                    defaultCategory = defaultCategory.ifBlank { "Importado" },
                                ) {
                                    parsed = null
                                    url = ""
                                    sheetName = ""
                                    defaultCategory = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Text("Importar ${result.rows.size} gastos")
                        }
                        if (cards.isEmpty()) {
                            Text(
                                "Primero creá una tarjeta para poder importar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Para Google Sheets: compartí la hoja como \"cualquier persona con el enlace\" " +
                    "y pegá la URL. Si la hoja tiene varias pestañas, escribí el nombre de la " +
                    "pestaña (por ejemplo Octubre).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El CSV exportado desde la hoja también sirve: elegí \"Archivo\" y seleccionalo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            MonnyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Zona peligrosa",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Borra todas las tarjetas, gastos, categorías con sus presupuestos, " +
                            "pagos y sobres de este teléfono. No se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showWipe = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text("Borrar todos los datos")
                    }
                }
            }
        }
    }

    pendingJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingJson = null },
            title = { Text("Restaurar respaldo") },
            text = {
                Text(
                    "Se van a borrar los datos actuales y reemplazarlos por los del archivo. " +
                        "Esta acción no se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importBackup(json) { }
                    pendingJson = null
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showWipe) {
        var confirmation by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text("Borrar todos los datos") },
            text = {
                Column {
                    Text(
                        "Se van a borrar las tarjetas, los gastos, las categorías con sus " +
                            "presupuestos y los sobres. Escribí BORRAR para confirmar.",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.uppercase().trim() },
                        label = { Text("BORRAR") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmation == "BORRAR",
                    onClick = {
                        vm.wipeAll { }
                        showWipe = false
                    },
                ) { Text("Borrar todo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipe = false }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

private fun fetchCsv(rawUrl: String, sheet: String): String {
    val finalUrl = buildSheetUrl(rawUrl.trim(), sheet.trim())
    val connection = URL(finalUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 25_000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Monny")
    return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun buildSheetUrl(url: String, sheet: String): String {
    val id = Regex("/spreadsheets/d/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)
        ?: return url
    val base = "https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv"
    if (sheet.isBlank()) return base
    val encoded = URLEncoder.encode(sheet, "UTF-8").replace("+", "%20")
    return "$base&sheet=$encoded"
}
