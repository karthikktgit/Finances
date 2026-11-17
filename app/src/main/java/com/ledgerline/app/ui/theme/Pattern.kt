package com.ledgerline.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * A faint pattern of small money icons (cart, card, home, savings jar...) behind a page, tilted alternately, so the
 * background has texture instead of being a flat colour. Barely visible: it never competes with the content.
 */
@Composable
fun Modifier.iconPattern(): Modifier {
    val painters = listOf(
        Icons.Filled.Savings, Icons.Filled.ShoppingCart, Icons.Filled.Home, Icons.Filled.CreditCard,
        Icons.Filled.AttachMoney, Icons.Filled.Receipt, Icons.Filled.LocalGasStation, Icons.Filled.Restaurant,
    ).map { rememberVectorPainter(it) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (dark) 0.06f else 0.04f)
    return this.drawWithCache {
        val step = 64.dp.toPx()
        val icon = 18.dp.toPx()
        val filter = ColorFilter.tint(tint)
        onDrawBehind {
            var row = 0
            var y = 0f
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                var col = 0
                while (x < size.width) {
                    val painter = painters[(row * 3 + col) % painters.size]
                    translate(x + (step - icon) / 2, y + (step - icon) / 2) {
                        rotate(if ((row + col) % 2 == 0) -14f else 14f, pivot = Offset(icon / 2, icon / 2)) {
                            with(painter) { draw(Size(icon, icon), colorFilter = filter) }
                        }
                    }
                    x += step
                    col++
                }
                y += step * 0.86f
                row++
            }
        }
    }
}
