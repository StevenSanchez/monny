package cr.monny.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reglas de los ciclos de facturación.
 *
 * Un ciclo queda identificado por su **fecha de corte** ([cutKey]): el día en que
 * el banco cierra el estado de cuenta. Por ejemplo, con corte el día 4 el ciclo
 * de octubre va del 5 de octubre al 4 de noviembre y su `cutKey` es 2026-11-04.
 *
 * El nombre visible del ciclo es el mes en que **arranca** (octubre en ese ejemplo),
 * para que coincida con lo que el usuario ve en su estado de cuenta.
 */
object CycleMath {
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CR"))
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CR"))
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CR"))

    /** Fecha de corte del mes indicado, ajustada al último día si el mes es más corto. */
    fun cutInMonth(year: Int, month: Int, cutDay: Int): LocalDate {
        val lastDay = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, cutDay.coerceIn(1, lastDay))
    }

    /** Corte que cierra el ciclo al que pertenece [date] (incluye el propio día de corte). */
    fun cycleKeyFor(date: LocalDate, cutDay: Int): LocalDate {
        val thisMonth = cutInMonth(date.year, date.monthValue, cutDay)
        return if (!date.isAfter(thisMonth)) {
            thisMonth
        } else {
            val next = date.plusMonths(1)
            cutInMonth(next.year, next.monthValue, cutDay)
        }
    }

    /** Corte anterior al indicado. */
    fun previousCut(cutKey: LocalDate, cutDay: Int): LocalDate {
        val prev = cutKey.minusMonths(1)
        return cutInMonth(prev.year, prev.monthValue, cutDay)
    }

    /** Corte siguiente al indicado. */
    fun nextCutKey(cutKey: LocalDate, cutDay: Int): LocalDate {
        val next = cutKey.plusMonths(1)
        return cutInMonth(next.year, next.monthValue, cutDay)
    }

    /** Primer día del ciclo (día siguiente al corte anterior). */
    fun cycleStart(cutKey: LocalDate, cutDay: Int): LocalDate = previousCut(cutKey, cutDay).plusDays(1)

    fun cycleEnd(cutKey: LocalDate): LocalDate = cutKey

    /** Fecha de vencimiento del pago. */
    fun paymentDue(cutKey: LocalDate, dueDays: Int): LocalDate = cutKey.plusDays(dueDays.toLong())

    /**
     * Corte al que pertenece una compra. Si [cutDayInNextCycle] está activo,
     * una compra hecha el mismo día del corte cuenta para el ciclo siguiente
     * (el banco ya cerró el estado de cuenta).
     */
    fun purchaseCycleKey(date: LocalDate, cutDay: Int, cutDayInNextCycle: Boolean): LocalDate {
        if (!cutDayInNextCycle) return cycleKeyFor(date, cutDay)
        val cutThisMonth = cutInMonth(date.year, date.monthValue, cutDay)
        return if (date.isBefore(cutThisMonth)) {
            cutThisMonth
        } else {
            val next = date.plusMonths(1)
            cutInMonth(next.year, next.monthValue, cutDay)
        }
    }

    /** Próximo corte respecto a hoy. */
    fun nextCut(cutDay: Int, today: LocalDate = LocalDate.now()): LocalDate = cycleKeyFor(today, cutDay)

    /** Corte más reciente que ya pasó (o que es hoy). */
    fun lastCut(cutDay: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val thisMonth = cutInMonth(today.year, today.monthValue, cutDay)
        return if (!today.isBefore(thisMonth)) thisMonth else previousCut(thisMonth, cutDay)
    }

    /** "Octubre 2026" */
    fun monthLabel(date: LocalDate): String =
        date.format(monthFormatter).replaceFirstChar { it.uppercase() }

    /** "4 nov" */
    fun shortDate(date: LocalDate): String = date.format(shortFormatter)

    /** "Miércoles 4 de noviembre" */
    fun longDate(date: LocalDate): String = date.format(dayFormatter).replaceFirstChar { it.uppercase() }

    /** Cuántos días faltan para el corte (0 = hoy). */
    fun daysToCut(cutDay: Int, today: LocalDate = LocalDate.now()): Long =
        nextCut(cutDay, today).toEpochDay() - today.toEpochDay()
}
