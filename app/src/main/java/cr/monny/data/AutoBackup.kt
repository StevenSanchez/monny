package cr.monny.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Respaldo automático diario en Descargas/Monny (Android 10+).
 *
 * Guarda un JSON por día y conserva los últimos 5. No pide permisos: usa MediaStore.
 * Sirve como red de seguridad antes de actualizar la app.
 */
object AutoBackup {
    private val FOLDER = "${Environment.DIRECTORY_DOWNLOADS}/Monny"
    private const val KEEP = 5

    /** Ejecuta el respaldo si todavía no existe el del día. */
    suspend fun runIfNeeded(context: Context, repository: MonnyRepository) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            withContext(Dispatchers.IO) {
                val name = "monny-${LocalDate.now()}.json"
                val resolver = context.contentResolver
                if (exists(resolver, name)) return@withContext

                val json = repository.exportJson()
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                prune(resolver)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exists(resolver: android.content.ContentResolver, name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(name, "$FOLDER%")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun prune(resolver: android.content.ContentResolver) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("monny-%.json", "$FOLDER%")
        val ids = mutableListOf<Long>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        ids.drop(KEEP).forEach { id ->
            runCatching {
                resolver.delete(
                    android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        id,
                    ),
                    null,
                    null,
                )
            }
        }
    }
}
