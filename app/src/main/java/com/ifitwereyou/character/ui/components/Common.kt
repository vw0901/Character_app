package com.ifitwereyou.character.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ifitwereyou.character.data.SaveState
import com.ifitwereyou.character.ui.Notice
import com.ifitwereyou.character.ui.NoticeKind
import com.ifitwereyou.character.ui.theme.Amber
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
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
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
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
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

/**
 * 手机端统一顶栏：返回键 + 标题/副标题 + 右侧操作。
 * 不重复消费状态栏 inset，由外层 App 统一处理安全区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Parchment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = Parchment,
                        )
                    }
                }
            },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Ink.copy(alpha = 0.93f),
                titleContentColor = Parchment,
                navigationIconContentColor = Parchment,
                actionIconContentColor = Parchment,
            ),
        )
        HorizontalDivider(color = Border.copy(alpha = 0.8f))
    }
}

/** 顶栏右侧的标准 48dp 图标按钮。 */
@Composable
fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Parchment else Muted.copy(alpha = 0.35f),
        )
    }
}

data class MenuAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** 顶栏「更多」菜单，用来收纳低频或危险操作。 */
@Composable
fun OverflowMenu(items: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TopBarIcon(
            icon = Icons.Outlined.MoreVert,
            contentDescription = "更多操作",
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Card),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.enabled) Parchment else Muted.copy(alpha = 0.4f),
                        )
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/**
 * 标准页面壳：固定顶栏 + 可滚动正文 + 可选吸底主操作。
 * 正文底部会预留底部操作栏的高度，避免最后一块内容被遮住。
 */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(bottom = if (bottomBar == null) 16.dp else 84.dp),
                content = content,
            )
            if (bottomBar != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = Ink,
                    shadowElevation = 16.dp,
                ) {
                    Column {
                        HorizontalDivider(color = Border)
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                            bottomBar()
                        }
                    }
                }
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
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = if (filled) 1f else 0.30f), shape)
            .then(click)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
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
fun CompactAction(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
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
