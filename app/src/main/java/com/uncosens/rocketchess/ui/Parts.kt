package com.uncosens.rocketchess.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncosens.rocketchess.R

/** The rocket-on-board mark with rounded corners. */
@Composable
fun RocketMark(size: Dp) {
    Image(painterResource(R.drawable.ic_rocket_mark), null, Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
}

/** Small upper-case label above a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = Mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 44dp rounded square icon button. */
@Composable
fun SquareIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(painterResource(icon), description, Modifier.size(20.dp))
    }
}

/** Back button, a centered title, an optional subtitle and an optional button on the right, which should be [buttonSize]. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, subtitle: String? = null, buttonSize: Dp = 44.dp, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(buttonSize + 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SquareIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), onBack, size = buttonSize)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        if (action != null) action() else Spacer(Modifier.size(buttonSize))
    }
}

/** Board side on a portrait screen: 90% of the screen width, smaller when the rest of the screen needs [rest] of the height. */
@Composable
fun BoxWithConstraintsScope.boardSide(rest: Dp): Dp =
    // Never under 60% of the width: on a very short screen the rest scrolls instead.
    minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f, maxHeight - rest).coerceAtLeast(maxWidth * 0.6f)

/**
 * One text style for a row of buttons: the largest size, down to 12sp, at which every label fits on one line
 * in [slot] minus [chrome] (the button's padding and icon). So buttons side by side share one text size.
 * Null when they do not fit even at 12sp.
 */
@Composable
fun fitLabelsOrNull(labels: List<String>, slot: Dp, chrome: Dp = 48.dp, base: TextStyle = MaterialTheme.typography.labelLarge): TextStyle? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val room = with(density) { (slot - chrome).roundToPx() }
    return remember(labels, room, base, density) {
        fun fits(size: Float) = labels.all { measurer.measure(it, base.copy(fontSize = size.sp), maxLines = 1).size.width <= room }
        generateSequence(base.fontSize.value) { it - 0.5f }.takeWhile { it >= MIN_LABEL_SP }.firstOrNull(::fits)?.let { base.copy(fontSize = it.sp) }
    }
}

/** Like [fitLabelsOrNull], but the smallest size (with cut labels) when nothing fits. */
@Composable
fun fitLabels(labels: List<String>, slot: Dp, chrome: Dp = 48.dp, base: TextStyle = MaterialTheme.typography.labelLarge): TextStyle =
    fitLabelsOrNull(labels, slot, chrome, base) ?: base.copy(fontSize = MIN_LABEL_SP.sp)

private const val MIN_LABEL_SP = 12f

/** Equal-width buttons, [columns] to a row, with one shared label size. [fixed] is the width of other items in the row, gaps included. */
@Composable
fun EvenRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
    columns: Int = labels.size,
    spacing: Dp = 8.dp,
    chrome: Dp = 48.dp,
    fixed: Dp = 0.dp,
    content: @Composable RowScope.(TextStyle) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val style = fitLabels(labels, (maxWidth - fixed - spacing * (columns - 1)) / columns, chrome)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) { content(style) }
    }
}

/** A row of choices with one shared label size; the check mark of the picked one is counted in. */
@Composable
fun Choices(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Segment padding 12dp a side, plus the 18dp check mark and its 8dp gap.
        val style = fitLabels(labels, maxWidth / labels.size, chrome = 50.dp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(selected = i == selected, onClick = { onPick(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) {
                    Text(label, style = style, maxLines = 1)
                }
            }
        }
    }
}
