package com.ifitwereyou.character.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ifitwereyou.character.data.SaveState
import com.ifitwereyou.character.ui.Notice
import com.ifitwereyou.character.ui.NoticeKind
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.AmberLight
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.Ink
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary

@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Amber.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.16f),
                    radius = size.minDimension * 0.72f,
                ),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.18f, size.height * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Amber.copy(alpha = 0.035f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.86f),
                    radius = size.minDimension * 0.6f,
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.86f),
            )
        }
        content()
    }
}

@Composable
fun EmberButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Ink,
            disabledContainerColor = Amber.copy(alpha = 0.35f),
            disabledContentColor = Ink.copy(alpha = 0.65f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Parchment,
            disabledContentColor = Muted.copy(alpha = 0.5f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    backgroundColor: Color = Card,
    borderColor: Color = Border,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .then(clickModifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    CardBox(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Parchment,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Muted,
                )
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider(color = Border)
                Column(Modifier.padding(16.dp)) { content() }
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Parchment)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
fun SaveStateText(state: SaveState) {
    Text(
        text = state.label,
        style = MaterialTheme.typography.labelSmall,
        color = if (state == SaveState.ERROR) Crimson else Muted,
    )
}

@Composable
fun NoticeBanner(notice: Notice, modifier: Modifier = Modifier) {
    val color = when (notice.kind) {
        NoticeKind.SUCCESS -> Forest
        NoticeKind.ERROR -> Crimson
        NoticeKind.INFO -> Amber
    }
    val icon = when (notice.kind) {
        NoticeKind.SUCCESS -> Icons.Outlined.CheckCircle
        NoticeKind.ERROR -> Icons.Outlined.Warning
        NoticeKind.INFO -> Icons.Outlined.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(notice.text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun InlineError(
    message: String,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Crimson.copy(alpha = 0.10f))
            .border(1.dp, Crimson.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Crimson,
            modifier = Modifier.weight(1f),
        )
        if (onOpenSettings != null) {
            TextButton(onClick = onOpenSettings) {
                Text("AI 设置", style = MaterialTheme.typography.labelSmall, color = Amber)
            }
        }
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Amber,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    val click = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = if (filled) 1f else 0.30f), shape)
            .then(click)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) Ink else color,
        )
    }
}

@Composable
fun StatMeter(
    icon: ImageVector,
    value: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = Secondary,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.width(24.dp),
        )
    }
}

@Composable
fun AppSpinner(modifier: Modifier = Modifier, size: Int = 22) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        color = Amber,
        trackColor = Secondary,
        strokeWidth = 2.dp,
    )
}

@Composable
fun LoadingBlock(
    title: String,
    subtitle: String? = null,
    rawText: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppSpinner(size = 34)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Parchment)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }
        if (rawText.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            CardBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Card.copy(alpha = 0.8f),
            ) {
                Text(
                    text = rawText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = Muted,
                    maxLines = 12,
                )
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Muted.copy(alpha = 0.8f),
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun RowSpacer(width: Int = 8) {
    Spacer(Modifier.width(width.dp))
}

@Composable
fun CompactAction(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Parchment,
    unfocusedTextColor = Parchment,
    disabledTextColor = Muted,
    focusedContainerColor = Secondary,
    unfocusedContainerColor = Secondary,
    disabledContainerColor = Secondary.copy(alpha = 0.5f),
    focusedBorderColor = Amber.copy(alpha = 0.55f),
    unfocusedBorderColor = Border,
    disabledBorderColor = Border,
    cursorColor = Amber,
    focusedLabelColor = Amber,
    unfocusedLabelColor = Muted,
    focusedPlaceholderColor = Muted.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = Muted.copy(alpha = 0.5f),
    focusedLeadingIconColor = Amber,
    unfocusedLeadingIconColor = Muted,
    focusedTrailingIconColor = Amber,
    unfocusedTrailingIconColor = Muted,
    focusedSupportingTextColor = Amber,
    unfocusedSupportingTextColor = Muted,
)

@Composable
fun RefreshRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Amber)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(color = Border, modifier = modifier)
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Muted,
        modifier = modifier,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Amber,
        modifier = modifier,
    )
}

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> Forest
        score >= 60 -> Amber
        else -> Crimson
    }
    Box(modifier = modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = Secondary,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2),
            )
        }
        Text("$score", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
fun AmberLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = AmberLight,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}
