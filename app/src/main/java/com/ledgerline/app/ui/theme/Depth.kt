package com.ledgerline.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card surface: flat, one step lighter than the page, with a hairline edge instead of a shadow, so cards sit on the
 * page cleanly without looking raised or glossy. Used by every card in the app. [elevation] is kept for callers and
 * ignored.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.depth(shape: Shape = RoundedCornerShape(20.dp), elevation: Dp = 0.dp): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    this
        .clip(shape)
        .background(scheme.surface)
        .border(1.dp, scheme.outlineVariant, shape)
}

/** The same for a surface that has its own colour, such as a tinted banner: a soft wash of [tint], no gloss. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.depthTinted(tint: Color, shape: Shape = RoundedCornerShape(20.dp), elevation: Dp = 0.dp): Modifier = this
    .clip(shape)
    .background(tint)
