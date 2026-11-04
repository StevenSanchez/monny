package cr.monny.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Modelo de datos de Monny (Room / SQLite).
 *
 * Convenciones:
 *  - Todos los montos se guardan en céntimos de colón (Long) para evitar errores de redondeo.
 *  - Las fechas se guardan como `epochDay` (días desde 1970-01-01) para poder ordenar y comparar.
 *  - `cycleKey` identifica un ciclo de facturación: es la fecha de corte que lo cierra.
 */

/**
 * Tarjeta de crédito. El ciclo se calcula a partir de [cutDay]:
 * cierra el día [cutDay] de cada mes (ajustado al último día del mes)
 * y el pago vence [paymentDueDays] días después.
 */
@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String = "",
    val last4: String = "",
    val colorHex: String = "#1F5C4B",
    /** Día del mes en que corta (1-31). */
    val cutDay: Int = 1,
    /** Días entre el corte y el vencimiento del pago (normalmente 14). */
    val paymentDueDays: Int = 14,
    val notifyCut: Boolean = true,
    val notifyDaysBeforeCut: Int = 3,
    val notifyPayment: Boolean = true,
    val archived: Boolean = false,
    /** Si una compra del mismo día de corte cuenta para el ciclo siguiente. */
    @androidx.room.ColumnInfo(defaultValue = "1")
    val cutDayInNextCycle: Boolean = true,
)

/** Categoría de gasto. [budgetCents] es opcional (0 = sin presupuesto). */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconKey: String = "otros",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** Presupuesto por ciclo en céntimos. 0 = sin presupuesto. */
    val budgetCents: Long = 0,
)

/**
 * Gasto individual. Puede ser un gasto normal, una cuota de una compra a tasa 0
 * ([planId]) o un cobro de un gasto fijo ([recurringId]).
 */
@Entity(
    tableName = "expenses",
    indices = [Index("cardId"), Index("cycleKey"), Index("categoryId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val categoryId: Long,
    /** Fecha del gasto en epochDay. */
    val date: Long,
    /** Fecha de corte (epochDay) del ciclo al que pertenece. */
    val cycleKey: Long,
    /** Monto en céntimos de colón (siempre positivo). */
    val amountCents: Long,
    val merchant: String,
    val note: String = "",
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Si la cuota viene de una compra a tasa 0. */
    val planId: Long? = null,
    val installmentIndex: Int? = null,
    val installmentCount: Int? = null,
    /** Porcentaje que le corresponde a la otra persona (0 = no compartido). */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val sharedPercent: Int = 0,
    /** Si viene de un gasto fijo mensual. */
    val recurringId: Long? = null,
)

/** Pago (abono) a la tarjeta para un ciclo. */
@Entity(
    tableName = "payments",
    indices = [Index("cardId"), Index("cycleKey")],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val cycleKey: Long,
    val date: Long,
    val amountCents: Long,
    val note: String = "",
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Dinero que se aparta para pagar un ciclo ("en sobre").
 * Un monto negativo representa un retiro (emergencia).
 */
@Entity(
    tableName = "covers",
    indices = [Index("cardId"), Index("cycleKey")],
)
data class Cover(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val cycleKey: Long,
    val date: Long,
    val amountCents: Long,
    /** Sobre del que salió el dinero, si aplica. */
    val envelopeId: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Sobre de ahorro (tipo BCR). El saldo es la suma de sus movimientos. */
@Entity(tableName = "envelopes")
data class Envelope(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#D9A441",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Movimiento de un sobre: positivo = entra dinero, negativo = sale. */
@Entity(
    tableName = "envelope_txs",
    indices = [Index("envelopeId")],
)
data class EnvelopeTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val envelopeId: Long,
    val date: Long,
    /** Positivo = entra dinero, negativo = sale. */
    val amountCents: Long,
    val note: String = "",
    val kind: String = "aporte",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Compra a tasa 0: un monto dividido en cuotas mensuales.
 * Las cuotas se materializan como [Expense] en sus ciclos correspondientes.
 */
@Entity(tableName = "plans")
data class InstallmentPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val categoryId: Long,
    val merchant: String,
    val totalCents: Long,
    val months: Int,
    /** Fecha de la compra (epochDay). */
    val firstDate: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Gasto fijo mensual (seguro, suscripción, etc.). */
@Entity(tableName = "recurring")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val categoryId: Long,
    val merchant: String,
    val amountCents: Long,
    /** Fecha del primer cobro (epochDay). */
    val firstDate: Long,
    val note: String = "",
    val active: Boolean = true,
    val sharedPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Marca qué ciclos ya generó cada gasto fijo.
 * Evita duplicados y que un cobro borrado a mano vuelva a aparecer.
 */
@Entity(tableName = "recurring_charges", primaryKeys = ["ruleId", "cycleKey"])
data class RecurringCharge(
    val ruleId: Long,
    val cycleKey: Long,
)
