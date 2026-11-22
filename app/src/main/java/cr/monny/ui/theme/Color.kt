package cr.monny.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta de la app.
 *
 * Esmeralda profundo como color principal, oro viejo como acento y un fondo
 * cálido tipo papel. La idea es que se sienta como una libreta de cuero con
 * detalles dorados: sobria, cálida y con jerarquía clara.
 *
 * [PaletteHex] se usa para categorías, sobres y tarjetas.
 */

// Marca: esmeralda
val Green900 = Color(0xFF06201A)
val Green800 = Color(0xFF0B3327)
val Green700 = Color(0xFF14503C)
val Green600 = Color(0xFF1C6A4F)
val Green500 = Color(0xFF2C8262)
val Green300 = Color(0xFF8FD9B9)
val Green100 = Color(0xFFCFEBDD)
val Green50 = Color(0xFFEAF6EF)

// Acento: oro viejo
val Gold = Color(0xFFC9A227)
val GoldSoft = Color(0xFFE6CE8C)
val Amber500 = Gold
val Amber200 = Color(0xFFF1E4C0)
val Amber700 = Color(0xFF8A6712)

// Neutros cálidos
val Cream = Color(0xFFFAF7F0)
val Ink = Color(0xFF13201A)
val Paper = Color(0xFFFFFEFA)
val Sand = Color(0xFFF3EEE3)

// Semánticos
val Positive = Color(0xFF2E7D54)
val Negative = Color(0xFFB3261E)
val Warning = Color(0xFFC77C02)

// Paleta para categorías y sobres
val PaletteHex = listOf(
    "#14503C", // esmeralda
    "#C9A227", // oro
    "#3A6B8C", // azul acero
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
