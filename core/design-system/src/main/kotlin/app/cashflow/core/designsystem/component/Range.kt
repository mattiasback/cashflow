package app.cashflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius

/**
 * The design's range input (cf-range): a 6dp pill track filled in brand up to a 28dp thumb with
 * a card-coloured ring, 48dp tall so it is easy to grab. Snaps to [steps].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfRange(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier.fillMaxWidth().height(48.dp).semantics {
            contentDescription = label
            stateDescription = valueText
        },
        thumb = {
            Box(Modifier.size(28.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(c.brand).border(3.dp, c.surfaceCard, CircleShape))
        },
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(c.surfaceControl)) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(Radius.pill).background(c.brand))
            }
        },
        colors = SliderDefaults.colors(activeTickColor = c.brand.copy(alpha = 0f), inactiveTickColor = c.brand.copy(alpha = 0f)),
    )
}
