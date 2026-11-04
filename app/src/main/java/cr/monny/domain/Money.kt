package cr.monny.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formato y lectura de montos en colones.
 *
 * Internamente todos los montos se guardan en **céntimos** (Long) para no perder
 * precisión. La app muestra el símbolo ₡ y separadores de miles costarricenses.
 */
object Money {
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-CR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val whole = DecimalFormat("#,##0", symbols)
    private val withCents = DecimalFormat("#,##0.00", symbols)

    /** "₡1.234.567" o "-₡12.500". */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val body = if (abs % 100L == 0L) whole.format(abs / 100) else withCents.format(abs / 100.0)
        return "$sign₡$body"
    }

    /** Igual que [format] pero sin el símbolo de colón. */
    fun plain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val body = if (abs % 100L == 0L) whole.format(abs / 100) else withCents.format(abs / 100.0)
        return sign + body
    }

    /** Convierte lo que escribe la persona a céntimos. Ignora separadores. */
    fun parse(text: String): Long? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val value = digits.toLongOrNull() ?: return null
        return value * 100
    }

    /** Texto editable a partir de céntimos (sin símbolo ni decimales si no hacen falta). */
    fun toInput(cents: Long): String {
        if (cents == 0L) return ""
        return if (cents % 100L == 0L) (cents / 100).toString() else withCents.format(cents / 100.0)
    }

    /** Solo dígitos, para campos de monto con separador visual. */
    fun toDigits(cents: Long): String = if (cents <= 0) "" else (cents / 100).toString()
}
