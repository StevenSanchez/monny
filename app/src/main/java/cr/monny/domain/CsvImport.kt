package cr.monny.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Importador de gastos desde CSV / Google Sheets.
 *
 * Soporta dos formatos:
 *  1. "bloques": hojas con Fecha/Motivo/Monto repetido por categoría (el formato
 *     de las hojas de la familia).
 *  2. "tabla": una tabla plana con encabezados Fecha, Motivo, Monto y opcionalmente
 *     Categoría y Nota.
 *
 * Además detecta:
 *  - la celda "En sobre" del resumen (para importarla como apartado),
 *  - montos negativos ("Pago -9.000") como pagos,
 *  - filas sin fecha (se asignan luego al ciclo principal),
 *  - montos escritos dentro del texto ("Lentes ₡37.500,00").
 */

/** Un gasto leído de un CSV, ya normalizado. */
data class ImportedExpense(
    val date: LocalDate?,
    val merchant: String,
    val amountCents: Long,
    val categoryName: String?,
    val note: String = "",
)

/** Un pago (monto negativo en la hoja) leído de un CSV. */
data class ImportedPayment(
    val date: LocalDate?,
    val amountCents: Long,
    val note: String,
)

data class CsvParseResult(
    val rows: List<ImportedExpense>,
    val skipped: Int,
    val detectedFormat: String,
    val categories: List<String>,
    val dateFrom: LocalDate?,
    val dateTo: LocalDate?,
    val payments: List<ImportedPayment> = emptyList(),
    val enSobreCents: Long? = null,
    val undated: Int = 0,
) {
    val totalCents: Long get() = rows.sumOf { it.amountCents }
    val paymentsCents: Long get() = payments.sumOf { it.amountCents }
}

object CsvImport {

    private val dateFormats = listOf(        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
    )

    private val headerDate = setOf("fecha", "date", "dia")
    private val headerMerchant = setOf("motivo", "descripcion", "detalle", "comercio", "concepto", "merchant")
    private val headerAmount = setOf("monto", "importe", "amount", "valor", "total")
    private val headerCategory = setOf("categoria", "category", "rubro")
    private val headerNote = setOf("nota", "note", "observaciones")

    private val stopWords = listOf("sub-total", "subtotal", "total", "deuda")

    /** Normaliza texto para comparar encabezados (minúsculas, sin acentos). */
    fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    /** Parser CSV que respeta comillas y comas dentro de un campo. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        val s = text.removePrefix("\uFEFF")
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> {
                        sb.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(sb.toString())
                    sb.clear()
                }
                c == ';' && !s.contains(',') -> {
                    row.add(sb.toString())
                    sb.clear()
                }
                c == '\r' -> Unit
                c == '\n' -> {
                    row.add(sb.toString())
                    sb.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }

    /**
     * Convierte un texto de monto ("₡1.234,56", "1,234.56", "-9000") a céntimos.
     * Detecta si el último separador es decimal o de miles según cuántos dígitos tenga detrás.
     */
    fun parseAmount(raw: String): Long? {
        if (raw.isBlank()) return null
        val cleaned = raw.replace("₡", "").replace("$", "").replace(" ", "").replace("\u00A0", "")
            .replace("(", "-").replace(")", "")
        if (cleaned.isEmpty()) return null
        val negative = cleaned.startsWith("-")
        var body = cleaned.removePrefix("-").removePrefix("+")
        body = body.filter { it.isDigit() || it == '.' || it == ',' }
        if (body.isEmpty()) return null

        val lastDot = body.lastIndexOf('.')
        val lastComma = body.lastIndexOf(',')
        val decimalSep = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> if (body.length - lastComma - 1 in 1..2) ',' else null
            lastDot >= 0 -> if (body.length - lastDot - 1 in 1..2) '.' else null
            else -> null
        }

        val normalized = if (decimalSep == null) {
            body.replace(".", "").replace(",", "")
        } else {
            val integerPart = body.substring(0, body.lastIndexOf(decimalSep))
                .replace(".", "").replace(",", "")
            val decimals = body.substring(body.lastIndexOf(decimalSep) + 1)
            "$integerPart.$decimals"
        }

        val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        val cents = value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        return if (negative) -cents else cents
    }

    /** Formatos de fecha aceptados (dd/MM/yyyy, yyyy-MM-dd, etc.). */
    fun parseDate(raw: String): LocalDate? {
        val value = raw.trim().substringBefore("T")
        if (value.isBlank()) return null
        dateFormats.forEach { format ->
            runCatching { LocalDate.parse(value, format) }.getOrNull()?.let { return it }
        }
        return null
    }

    /** Si el monto está escrito dentro del texto ("Lentes ₡37.500,00"). */
    private fun amountFromText(text: String): Long? {
        val match = Regex("₡\\s*([0-9][0-9.,]*)").find(text) ?: return null
        return parseAmount(match.groupValues[1])
    }

    private fun isStopRow(row: List<String>): Boolean {
        val joined = normalize(row.joinToString(" "))
        return stopWords.any { joined.contains(it) }
    }

    /** En Google Sheets el encabezado puede venir como "… Gastos Comidas Fecha". */
    private fun isDateHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "fecha" || (normalized.length > 6 && normalized.endsWith("fecha"))
    }

    private fun categoryFromHeader(value: String): String? {
        val clean = value.trim()
        if (!normalize(clean).endsWith("fecha")) return null
        var label = clean.dropLast(5).trim().trimEnd(':', '-', '–').trim()
        val index = normalize(label).lastIndexOf("gastos")
        if (index >= 0) label = label.substring(index + 6).trim()
        val normalized = normalize(label)
        if (label.isEmpty() || normalized.contains("tarjeta")) return null
        return label.replaceFirstChar { it.uppercase() }
    }

    private fun categoryFromAbove(grid: List<List<String>>, headerRow: Int, column: Int): String? {
        if (column < 0) return null
        var probe = headerRow - 1
        while (probe >= 0 && probe >= headerRow - 4) {
            val value = grid.getOrNull(probe)?.getOrNull(column)?.trim().orEmpty()
            val normalized = normalize(value)
            if (value.isNotEmpty() && !normalized.contains("tarjeta") &&
                normalized !in setOf("fecha", "motivo", "monto", "total", "deuda")
            ) {
                val label = value.removePrefix("Gastos ").removePrefix("gastos ").trim()
                if (label.isNotEmpty()) return label.replaceFirstChar { it.uppercase() }
            }
            probe--
        }
        return null
    }

    private data class Block(
        val motivoCol: Int,
        val amountCol: Int,
        val dateCol: Int?,
        val category: String?,
    )

    /** Detecta el formato y devuelve los gastos, pagos y el "en sobre" encontrados. */
    fun detect(grid: List<List<String>>): CsvParseResult {
        val cleanGrid = grid.map { row -> row.map { it.trim() } }
        val enSobre = findEnSobre(cleanGrid)

        for ((rowIndex, row) in cleanGrid.withIndex()) {
            val motivoCols = row.indices.filter { normalize(row[it]) == "motivo" }
            if (motivoCols.size >= 2) {
                val blocks = motivoCols.mapNotNull { inferBlock(cleanGrid, rowIndex, it) }
                if (blocks.isNotEmpty()) {
                    return parseBlocks(cleanGrid, rowIndex, blocks, enSobre)
                }
            }
        }

        for ((rowIndex, row) in cleanGrid.withIndex()) {
            val dateCol = row.indexOfFirst { isDateHeader(it) }
            val amountCol = row.indexOfFirst { normalize(it) in headerAmount }
            val merchantCol = row.indexOfFirst { normalize(it) in headerMerchant }
            if (dateCol >= 0 && amountCol >= 0) {
                val categoryCol = row.indexOfFirst { normalize(it) in headerCategory }
                val noteCol = row.indexOfFirst { normalize(it) in headerNote }
                return parseTable(
                    cleanGrid, rowIndex, dateCol,
                    if (merchantCol >= 0) merchantCol else dateCol + 1,
                    amountCol,
                    if (categoryCol >= 0) categoryCol else null,
                    if (noteCol >= 0) noteCol else null,
                    enSobre,
                )
            }
        }

        return CsvParseResult(emptyList(), 0, "desconocido", emptyList(), null, null, enSobreCents = enSobre)
    }

    private fun inferBlock(grid: List<List<String>>, headerRow: Int, motivoCol: Int): Block? {
        val sample = (headerRow + 1 until minOf(grid.size, headerRow + 300))
            .map { grid[it] }
            .takeWhile { !isStopRow(it) }
        if (sample.isEmpty()) return null

        fun amountScore(column: Int) =
            sample.count { parseAmount(it.getOrNull(column).orEmpty()) != null }

        fun dateScore(column: Int) =
            sample.count { parseDate(it.getOrNull(column).orEmpty()) != null }

        val amountCol = listOf(motivoCol + 1, motivoCol + 2)
            .filter { it >= 0 }
            .maxByOrNull { amountScore(it) }
            ?: return null
        if (amountScore(amountCol) == 0) return null

        val dateCol = listOf(motivoCol - 1, motivoCol - 2)
            .filter { it >= 0 && it != amountCol }
            .maxByOrNull { dateScore(it) }
            ?.takeIf { dateScore(it) > 0 }

        val category = categoryFromAbove(grid, headerRow, dateCol ?: motivoCol)
            ?: categoryFromAbove(grid, headerRow, listOf(motivoCol - 1, motivoCol).firstOrNull { it >= 0 } ?: 0)
            ?: categoryFromHeader(grid[headerRow].getOrNull(motivoCol - 1).orEmpty())

        return Block(motivoCol, amountCol, dateCol, category)
    }

    private fun parseBlocks(
        grid: List<List<String>>,
        headerRow: Int,
        blocks: List<Block>,
        enSobre: Long?,
    ): CsvParseResult {
        val rows = mutableListOf<ImportedExpense>()
        val payments = mutableListOf<ImportedPayment>()
        var skipped = 0

        blocks.forEach { block ->
            for (r in headerRow + 1 until grid.size) {
                val row = grid[r]
                if (row.all { it.isBlank() }) continue
                if (isStopRow(row)) break

                val rawMerchant = row.getOrNull(block.motivoCol).orEmpty().trim()
                val fromColumn = parseAmount(row.getOrNull(block.amountCol).orEmpty())
                val fromText = if (fromColumn == null) amountFromText(rawMerchant) else null
                val amount = fromColumn ?: fromText
                val date = block.dateCol?.let { parseDate(row.getOrNull(it).orEmpty()) }

                if (amount == null) {
                    if (rawMerchant.isNotBlank()) skipped++
                    continue
                }
                val merchant = if (fromText != null) rawMerchant.substringBefore("₡").trim() else rawMerchant
                if (amount < 0) {
                    payments += ImportedPayment(date, -amount, merchant.ifBlank { "Pago" })
                    continue
                }
                if (amount == 0L) continue
                rows += ImportedExpense(date, merchant, amount, block.category)
            }
        }

        return buildResult(rows, payments, skipped, "bloques", enSobre)
    }

    private fun parseTable(
        grid: List<List<String>>,
        headerRow: Int,
        dateCol: Int,
        merchantCol: Int,
        amountCol: Int,
        categoryCol: Int?,
        noteCol: Int?,
        enSobre: Long?,
    ): CsvParseResult {
        val rows = mutableListOf<ImportedExpense>()
        val payments = mutableListOf<ImportedPayment>()
        var skipped = 0

        for (r in headerRow + 1 until grid.size) {
            val row = grid[r]
            if (row.all { it.isBlank() }) continue
            if (isStopRow(row)) continue

            val rawMerchant = row.getOrNull(merchantCol).orEmpty().trim()
            val fromColumn = parseAmount(row.getOrNull(amountCol).orEmpty())
            val fromText = if (fromColumn == null) amountFromText(rawMerchant) else null
            val amount = fromColumn ?: fromText
            val date = parseDate(row.getOrNull(dateCol).orEmpty())

            if (amount == null) {
                if (rawMerchant.isNotBlank()) skipped++
                continue
            }
            val merchant = if (fromText != null) rawMerchant.substringBefore("₡").trim() else rawMerchant
            if (amount < 0) {
                payments += ImportedPayment(date, -amount, merchant.ifBlank { "Pago" })
                continue
            }
            if (amount == 0L) continue
            rows += ImportedExpense(
                date = date,
                merchant = merchant,
                amountCents = amount,
                categoryName = categoryCol?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
                note = noteCol?.let { row.getOrNull(it)?.trim() }.orEmpty(),
            )
        }

        return buildResult(rows, payments, skipped, "tabla", enSobre)
    }

    private fun buildResult(
        rows: List<ImportedExpense>,
        payments: List<ImportedPayment>,
        skipped: Int,
        format: String,
        enSobre: Long?,
    ): CsvParseResult {
        val sorted = rows.sortedWith(compareBy { it.date ?: LocalDate.MAX })
        return CsvParseResult(
            rows = sorted,
            skipped = skipped,
            detectedFormat = format,
            categories = sorted.mapNotNull { it.categoryName }.distinct().sorted(),
            dateFrom = sorted.mapNotNull { it.date }.minOrNull(),
            dateTo = sorted.mapNotNull { it.date }.maxOrNull(),
            payments = payments.sortedWith(compareBy { it.date ?: LocalDate.MAX }),
            enSobreCents = enSobre,
            undated = rows.count { it.date == null },
        )
    }

    /** Busca la celda "En sobre" y toma el primer monto que tenga a la derecha. */
    private fun findEnSobre(grid: List<List<String>>): Long? {
        grid.forEach { row ->
            row.forEachIndexed { index, cell ->
                val normalized = normalize(cell)
                if (normalized == "en sobre" || normalized.endsWith("en sobre")) {
                    for (j in index + 1 until minOf(index + 4, row.size)) {
                        val value = parseAmount(row[j])
                        if (value != null && value > 0) return value
                    }
                }
            }
        }

        // Respaldo: algunas hojas pierden la etiqueta "En sobre" al exportarse.
        grid.forEach { row ->
            val totalIndex = row.indexOfFirst { normalize(it) == "total" }
            if (totalIndex >= 0 && totalIndex + 4 < row.size) {
                val label = row.getOrNull(totalIndex + 3).orEmpty()
                if (label.isBlank()) {
                    val value = parseAmount(row[totalIndex + 4])
                    if (value != null && value > 0) return value
                }
            }
        }
        return null
    }
}
