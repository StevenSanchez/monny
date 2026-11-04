package cr.monny

import android.app.Application
import cr.monny.data.AppSettings
import cr.monny.data.AutoBackup
import cr.monny.data.MonnyDb
import cr.monny.data.MonnyRepository
import cr.monny.notifications.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application: inicializa la base de datos, las preferencias, los recordatorios
 * y lanza el respaldo automático diario.
 */
class MonnyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: MonnyRepository
        private set

    lateinit var settings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MonnyRepository(MonnyDb.get(this))
        settings = AppSettings(this)
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
        appScope.launch { AutoBackup.runIfNeeded(this@MonnyApp, repository) }
    }
}
