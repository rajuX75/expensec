package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skill: component-family-consistency & brand-visual-language (Dembrandt Stage 2 & 4).
 *
 * All shape-bearing elements (cards, inputs, buttons, pills, sheets) derive from a single
 * concentric shape token DNA rather than arbitrary per-file radii.
 */
object ShapeTokens {
    /** Tags, chip badges, small indicators (8dp) */
    val small = RoundedCornerShape(8.dp)

    /** Buttons, text input fields, segmented buttons (12dp) */
    val medium = RoundedCornerShape(12.dp)

    /** Bento cards, list item cards, account/bill cards (16dp) */
    val large = RoundedCornerShape(16.dp)

    /** Hero summary cards, bottom sheet top edges, dialog surfaces (24dp) */
    val extraLarge = RoundedCornerShape(24.dp)

    /** Action pills, avatar frames, filter pills (full round) */
    val pill = RoundedCornerShape(999.dp)
    val circle = CircleShape
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = ShapeTokens.small,
    medium = ShapeTokens.medium,
    large = ShapeTokens.large,
    extraLarge = ShapeTokens.extraLarge
)

/**
 * Dembrandt "Shadow + 1px subtle border" rule:
 * On light surfaces, subtle elevation washes out without a defined edge.
 * On dark surfaces, subtle low-alpha borders define physical boundaries.
 */
@Composable
fun cardBorderStroke(
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
): BorderStroke = BorderStroke(1.dp, color)
