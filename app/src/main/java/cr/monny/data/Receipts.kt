package cr.monny.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Guarda las fotos de comprobantes en el almacenamiento privado de la app. */
object Receipts {
    private fun dir(context: Context): File =
        File(context.filesDir, "receipts").apply { mkdirs() }

    /** Copia la imagen elegida y devuelve su ruta interna. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val target = File(dir(context), "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
