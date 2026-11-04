package cr.monny

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cr.monny.data.ThemeMode
import cr.monny.ui.MonnyRoot
import cr.monny.ui.MonnyViewModel
import cr.monny.ui.theme.MonnyTheme

/**
 * Actividad única de la app. Crea el ViewModel compartido, aplica el tema
 * y pide permiso de notificaciones la primera vez.
 */
class MainActivity : ComponentActivity() {

    private val vm: MonnyViewModel by viewModels {
        val app = application as MonnyApp
        MonnyViewModel.Factory(app.repository, app.settings)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        askForNotifications()

        setContent {
            val app = application as MonnyApp
            val theme by app.settings.theme.collectAsStateWithLifecycle()
            MonnyTheme(theme) {
                MonnyRoot(
                    vm = vm,
                    theme = theme,
                    onThemeChange = app.settings::setTheme,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
