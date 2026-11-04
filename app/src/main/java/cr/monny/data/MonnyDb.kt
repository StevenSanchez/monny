package cr.monny.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local (SQLite con Room).
 *
 * Cada vez que cambia el esquema se sube [version] y se agrega una migración,
 * para que actualizar la app nunca borre los datos del usuario.
 */
@Database(
    entities = [
        Card::class,
        Category::class,
        Expense::class,
        Payment::class,
        Cover::class,
        Envelope::class,
        EnvelopeTx::class,
        InstallmentPlan::class,
        RecurringExpense::class,
        RecurringCharge::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class MonnyDb : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun categories(): CategoryDao
    abstract fun expenses(): ExpenseDao
    abstract fun payments(): PaymentDao
    abstract fun covers(): CoverDao
    abstract fun envelopes(): EnvelopeDao
    abstract fun plans(): PlanDao
    abstract fun recurring(): RecurringDao

    companion object {
        @Volatile
        private var instance: MonnyDb? = null

        /** v1 → v2: presupuesto opcional por categoría. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE categories ADD COLUMN budgetCents INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v2 → v3: compras a tasa 0 (plan + cuota en cada gasto). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN planId INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN installmentIndex INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN installmentCount INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`cardId` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`merchant` TEXT NOT NULL, " +
                        "`totalCents` INTEGER NOT NULL, " +
                        "`months` INTEGER NOT NULL, " +
                        "`firstDate` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /** v3 → v4: compras del día de corte al ciclo siguiente + gastos compartidos. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN cutDayInNextCycle INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE expenses ADD COLUMN sharedPercent INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v4 → v5: gastos fijos mensuales. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN recurringId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`cardId` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`merchant` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`firstDate` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`active` INTEGER NOT NULL, " +
                        "`sharedPercent` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /** v5 → v6: control de ciclos ya generados por cada gasto fijo. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_charges` (" +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`cycleKey` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ruleId`, `cycleKey`))",
                )
            }
        }

        /** Instancia única (singleton) de la base de datos. */
        fun get(context: Context): MonnyDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MonnyDb::class.java,
                "monny.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }
    }
}
