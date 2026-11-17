package com.ledgerline.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The Add button: a solid ink pill (white in dark mode) with a plus and the label. It sinks slightly when pressed.
 * The one strong shape on the page, so it is easy to find without decoration.
 */
@Composable
fun CashButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState().value
    val scale = animateFloatAsState(if (pressed) 0.96f else 1f, label = "add-press").value
    val shape = RoundedCornerShape(50)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .scale(scale)
            .shadow(if (pressed) 2.dp else 6.dp, shape)
            .clip(shape)
            .background(scheme.primary)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .height(50.dp)
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
        Text(label, color = scheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
