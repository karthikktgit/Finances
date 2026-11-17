package com.ledgerline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small number pad for amounts: three short rows instead of the phone keyboard, which covers half the screen.
 * Keys add to [text]; the screen's own amount rules clean it, so "12.5" still means 12.50.
 */
@Composable
fun AmountKeypad(text: String, onText: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "12345".forEach { c -> Key(c.toString()) { onText(text + c) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "67890".forEach { c -> Key(c.toString()) { onText(text + c) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key(".") { onText(text + ".") }
            Key("", description = "Delete") { onText(text.dropLast(1)) }
            Key("Done", weight = 3f, strong = true, onClick = onDone)
        }
    }
}

@Composable
private fun RowScope.Key(label: String, weight: Float = 1f, strong: Boolean = false, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .weight(weight)
            .height(42.dp)
            .clip(shape)
            .background(if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description ?: label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label.isEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, modifier = Modifier.size(20.dp))
        } else {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * An amount that opens the small keypad instead of the phone keyboard. Looks like the other fields; tap it to type.
 * The caller shows [AmountKeypad] under it while [open] is true.
 */
@Composable
fun AmountBox(text: String, symbol: String, label: String, open: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (open) 2.dp else 1.dp, if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable {
                // Close the phone keyboard if another field had it, so only the small pad shows.
                focus.clearFocus()
                keyboard?.hide()
                onOpen()
            }
            .semantics { contentDescription = "$label ${text.ifEmpty { "0" }}" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$symbol ${text.ifEmpty { "0" }}",
            style = MaterialTheme.typography.titleMedium,
            color = if (text.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
