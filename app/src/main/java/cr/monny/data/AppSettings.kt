package cr.monny.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias simples de la app (tema y función de gastos compartidos). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("monny", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    private val _sharedEnabled = MutableStateFlow(prefs.getBoolean("shared", false))
    val sharedEnabled: StateFlow<Boolean> = _sharedEnabled

    fun setSharedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("shared", enabled).apply()
        _sharedEnabled.value = enabled
    }

    var lastCardId: Long
        get() = prefs.getLong("lastCard", 0L)
        set(value) = prefs.edit().putLong("lastCard", value).apply()
}
