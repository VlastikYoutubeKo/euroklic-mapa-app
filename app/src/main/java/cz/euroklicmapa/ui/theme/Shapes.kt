package cz.euroklicmapa.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.data.prefs.DesignVariant

@Immutable
data class ExtendedShapes(
    val card: CornerBasedShape,
    val small: CornerBasedShape,
    val pill: CornerBasedShape,
    val sheet: CornerBasedShape,
    val fab: CornerBasedShape,
    val tile: CornerBasedShape,
    // Preserve the existing 14/16/20dp distinctions in SOUCASNY.
    val cardLarge: CornerBasedShape = card,
    val round: CornerBasedShape = CircleShape,
    val outlineWidth: Dp = 1.dp,
    val sheetTop: CornerBasedShape = sheet,
)

internal fun designShapes(variant: DesignVariant): ExtendedShapes = when (variant) {
    DesignVariant.SOUCASNY -> ExtendedShapes(
        RoundedCornerShape(16.dp), RoundedCornerShape(12.dp), RoundedCornerShape(999.dp),
        RoundedCornerShape(28.dp), RoundedCornerShape(16.dp), RoundedCornerShape(14.dp),
        cardLarge = RoundedCornerShape(20.dp),
        sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    )
    DesignVariant.KONTRAST -> ExtendedShapes(
        RoundedCornerShape(8.dp), RoundedCornerShape(6.dp), RoundedCornerShape(10.dp),
        RoundedCornerShape(12.dp), RoundedCornerShape(10.dp), RoundedCornerShape(6.dp),
        round = RoundedCornerShape(10.dp), outlineWidth = 2.dp,
    )
    DesignVariant.SVEZI -> ExtendedShapes(
        RoundedCornerShape(20.dp), RoundedCornerShape(16.dp), RoundedCornerShape(999.dp),
        RoundedCornerShape(32.dp), CircleShape, CircleShape,
    )
    DesignVariant.ZNACENI -> ExtendedShapes(
        RoundedCornerShape(4.dp), RoundedCornerShape(4.dp), RoundedCornerShape(4.dp),
        RoundedCornerShape(6.dp), RoundedCornerShape(4.dp), RoundedCornerShape(4.dp),
        round = RoundedCornerShape(4.dp),
    )
}

internal fun designMaterialShapes(variant: DesignVariant): Shapes =
    if (variant == DesignVariant.SOUCASNY) Shapes() else {
        val shapes = designShapes(variant)
        Shapes(extraSmall = shapes.small, small = shapes.small, medium = shapes.card, large = shapes.card, extraLarge = shapes.sheet)
    }

val LocalExtendedShapes = staticCompositionLocalOf { designShapes(DesignVariant.SOUCASNY) }

/** High contrast surfaces get a visible boundary; existing surfaces keep their original border. */
@Composable
fun contrastBorder(): BorderStroke? = if (EuroklicTheme.variant == DesignVariant.KONTRAST)
    BorderStroke(EuroklicTheme.shapes.outlineWidth, MaterialTheme.colorScheme.outline) else null

@Composable
fun variantFabModifier(): Modifier = if (EuroklicTheme.variant == DesignVariant.KONTRAST)
    Modifier.border(3.dp, EuroklicTheme.extended.selectedChip, EuroklicTheme.shapes.fab) else Modifier

@Composable
fun variantFabElevation() = if (EuroklicTheme.variant == DesignVariant.KONTRAST)
    FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp)
else FloatingActionButtonDefaults.elevation()
