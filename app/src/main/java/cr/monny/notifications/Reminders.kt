package cr.monny.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cr.monny.MainActivity
import cr.monny.R
import cr.monny.data.MonnyDb
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Recordatorios locales (WorkManager).
 *
 * Corre un worker diario a las 9:00 que revisa cada tarjeta:
 *  - avisa N días antes del corte (configurable por tarjeta),
 *  - avisa el día de vencimiento del pago.
 *
 * No usa servidores: todo se programa en el teléfono.
 */
object Reminders {
    private const val CHANNEL_ID = "recordatorios"
    private const val WORK_NAME = "monny-recordatorios-diarios"

    /** Crea el canal de notificaciones (necesario desde Android 8). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos de corte y fecha de pago de tus tarjetas"
        }
        manager.createNotificationChannel(channel)
    }

    /** Programa (o reprograma) el chequeo diario a las 9:00. */
    fun schedule(context: Context) {
        ensureChannel(context)
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = java.time.Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Muestra una notificación si el usuario dio permiso. */
    fun notify(context: Context, id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monny)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Worker diario que revisa cortes y vencimientos. */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Reminders.ensureChannel(context)
        val today = LocalDate.now()
        val db = MonnyDb.get(context)
        val cards = db.cards().allOnce()

        cards.forEach { card ->
            val cutKey = CycleMath.nextCut(card.cutDay, today)
            val days = cutKey.toEpochDay() - today.toEpochDay()

            if (card.notifyCut && days == card.notifyDaysBeforeCut.toLong()) {
                val spent = db.expenses().totalForCycle(card.id, cutKey.toEpochDay())
                Reminders.notify(
                    context = context,
                    id = (card.id * 10 + 1).toInt(),
                    title = "Corte de ${card.name}",
                    message = "El corte es en $days ${if (days == 1L) "día" else "días"} " +
                        "(${CycleMath.shortDate(cutKey)}). Llevás gastado ${Money.format(spent)}.",
                )
            }

            val lastCut = CycleMath.lastCut(card.cutDay, today)
            val due = CycleMath.paymentDue(lastCut, card.paymentDueDays)
            if (card.notifyPayment && due == today) {
                Reminders.notify(
                    context = context,
                    id = (card.id * 10 + 2).toInt(),
                    title = "Pago de ${card.name}",
                    message = "Hoy vence el pago del corte del ${CycleMath.shortDate(lastCut)}.",
                )
            }
        }
        return Result.success()
    }
}
