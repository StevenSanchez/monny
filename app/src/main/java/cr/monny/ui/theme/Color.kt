package cr.monny.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta de la app.
 *
 * Verde profundo como color principal, ámbar como acento y un fondo tipo papel.
 * [PaletteHex] se usa para categorías, sobres y tarjetas.
 */

// Marca
val Green900 = Color(0xFF0B2E24)
val Green800 = Color(0xFF14442F)
val Green700 = Color(0xFF1F5C4B)
val Green600 = Color(0xFF2A7259)
val Green300 = Color(0xFF8FD8BE)
val Green100 = Color(0xFFC8EAD9)
val Green50 = Color(0xFFE8F5EE)

val Amber500 = Color(0xFFD9A441)
val Amber200 = Color(0xFFF3D9A4)
val Amber700 = Color(0xFF8A5E12)

val Cream = Color(0xFFFBF9F4)
val Ink = Color(0xFF151A17)
val Paper = Color(0xFFFFFFFF)

// Semánticos
val Positive = Color(0xFF2E7D54)
val Negative = Color(0xFFB3261E)
val Warning = Color(0xFFC77C02)

// Paleta para categorías y sobres
val PaletteHex = listOf(
    "#1F5C4B", // verde
    "#D9A441", // ámbar
    "#3A6B8C", // azul
    "#A64B7A", // magenta
    "#7A5CC4", // violeta
    "#C0603A", // terracota
    "#4E8C5A", // verde claro
    "#B5892F", // ocre
    "#5B6B7A", // gris azulado
    "#8C4E4E", // vino
)

fun hexToColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Green700)
