package cr.monny.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cr.monny.data.Category
import cr.monny.data.Expense
import cr.monny.domain.CycleMath
import cr.monny.domain.Money
import cr.monny.ui.theme.hexToColor
import cr.monny.ui.theme.LocalMonnyColors
import java.time.LocalDate

/*
 * Filas de la app: un gasto, un total por categoría.
 */

/** Fila de un gasto con su categoría, cuota (si aplica) y compartido. */
@Composable
fun ExpenseRow(
    expense: Expense,
    category: Category?,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onClick: () -> Unit = {},
) {
    val color = hexToColor(category?.colorHex ?: "#8A8F8B")
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CategoryIcon(
                    iconKey = category?.iconKey ?: "otros",
                    tint = color,
                    size = 20.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    expense.merchant.ifBlank { "Gasto" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(category?.name ?: "Sin categoría")
                        if (expense.installmentIndex != null && expense.installmentCount != null) {
                            append(" · Cuota ${expense.installmentIndex}/${expense.installmentCount}")
                        }
                        if (expense.sharedPercent > 0) {
                            append(" · Compartido ${expense.sharedPercent}%")
                        }
                        if (expense.recurringId != null) {
                            append(" · Fijo")
                        }
                        if (showDate) {
                            append(" · ")
                            append(CycleMath.shortDate(LocalDate.ofEpochDay(expense.date)))
                        }
                        if (expense.note.isNotBlank()) {
                            append(" · ")
                            append(expense.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                Money.format(expense.amountCents),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun CategoryTotalRow(
    name: String,
    color: Color,
    amountCents: Long,
    share: Float,
    count: Int,
    modifier: Modifier = Modifier,
    budgetCents: Long = 0,
) {
    val extra = LocalMonnyColors.current
    val hasBudget = budgetCents > 0
    val progress = if (hasBudget) amountCents.toFloat() / budgetCents else share
    val over = hasBudget && amountCents > budgetCents
    val barColor = if (over) extra.negative else color

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Dot(color, 10.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "($count)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(amountCents), style = MaterialTheme.typography.bodyLarge)
            if (hasBudget) {
                Text(
                    " / ${Money.format(budgetCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(barColor, RoundedCornerShape(50)),
            )
        }
        if (hasBudget) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (over) {
                    "Te pasaste ${Money.format(amountCents - budgetCents)}"
                } else {
                    "Te queda ${Money.format(budgetCents - amountCents)} de presupuesto"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (over) extra.negative else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
