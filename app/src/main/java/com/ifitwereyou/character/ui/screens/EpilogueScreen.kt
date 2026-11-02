package com.ifitwereyou.character.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.LoadingBlock
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.ScreenScaffold
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.GameTextStyle
import com.ifitwereyou.character.ui.theme.Ink
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary

@Composable
fun EpilogueScreen(
    viewModel: AppViewModel,
    onExport: () -> Unit,
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val ui by viewModel.gameState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val document = project
    val book = document?.activeAdventureKey?.let { document.adventures[it] }

    if (document == null || book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在打开后日谈…", color = Muted)
        }
        return
    }

    val success = book.outcome == AdventureOutcome.SUCCESS
    val title = book.story?.title?.takeIf { it.isNotBlank() } ?: "这段故事"
    val epilogueText = when {
        ui.isWritingEpilogue && ui.epilogueDraft.isNotBlank() -> ui.epilogueDraft
        else -> book.epilogue
    }
    val paragraphs = storyParagraphs(epilogueText)
    val hasEpilogue = book.epilogue.isNotBlank()

    ScreenScaffold(
        title = "后日谈",
        subtitle = "$title · ${book.character.name} · ${saveState.label}",
        onBack = viewModel::closeEpilogue,
        actions = {
            TopBarIcon(
                icon = Icons.Outlined.Share,
                contentDescription = "导出存档",
                onClick = onExport,
            )
            OverflowMenu(
                items = listOf(
                    MenuAction(
                        label = "重新书写后日谈",
                        enabled = !ui.isWritingEpilogue,
                        onClick = viewModel::rewriteEpilogue,
                    ),
                    MenuAction("AI 设置", onClick = viewModel::openSettings),
                ),
            )
        },
    ) {
        EpilogueHero(
            success = success,
            title = title,
            characterName = book.character.name,
            goalTitle = book.goal.title,
        )

        Spacer(Modifier.height(14.dp))
        if (ui.isWritingEpilogue && paragraphs.isEmpty()) {
            LoadingBlock(
                title = "正在书写后日谈…",
                subtitle = "基于结局页与这一路发生的事，写下结局之后的余波",
            )
        } else {
            if (paragraphs.isNotEmpty()) {
                EpilogueBody(text = epilogueText, paragraphs = paragraphs, writing = ui.isWritingEpilogue)
            }

            if (!ui.isWritingEpilogue && ui.epilogueError.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                InlineError(ui.epilogueError, onOpenSettings = viewModel::openSettings)
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    text = "重新书写后日谈",
                    icon = Icons.Outlined.Refresh,
                    onClick = viewModel::rewriteEpilogue,
                )
            }

            if (paragraphs.isEmpty() && !ui.isWritingEpilogue && ui.epilogueError.isBlank()) {
                Spacer(Modifier.height(12.dp))
                CardBox(
                    backgroundColor = Card.copy(alpha = 0.65f),
                    borderColor = Border,
                ) {
                    Column {
                        Text(
                            if (hasEpilogue) "后日谈已经写完。" else "这段故事还没有后日谈。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                        Spacer(Modifier.height(10.dp))
                        GhostButton(
                            text = if (hasEpilogue) "重新书写后日谈" else "生成后日谈",
                            icon = Icons.Outlined.Edit,
                            onClick = viewModel::rewriteEpilogue,
                        )
                    }
                }
            }

            if (paragraphs.isNotEmpty() && !ui.isWritingEpilogue) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = viewModel::rewriteEpilogue) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("重新书写", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Card.copy(alpha = 0.35f))
                .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "后日谈是可选的后话，只写结局之后发生的事，不会改写已经发生的结局。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun EpilogueHero(
    success: Boolean,
    title: String,
    characterName: String,
    goalTitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Secondary, Card, Card.copy(alpha = 0.92f)),
                ),
            )
            .border(
                1.dp,
                if (success) Amber.copy(alpha = 0.28f) else Crimson.copy(alpha = 0.28f),
                RoundedCornerShape(10.dp),
            )
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Amber.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, Amber.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("结局之后", style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Text(
                        "独立后话 · 不改变已发生的结局",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                Chip(
                    text = if (success) "成功结局" else "失败结局",
                    color = if (success) Forest else Crimson,
                )
            }
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Parchment)
            Spacer(Modifier.height(3.dp))
            Text(
                "以 $characterName 的身份 · 最终目标：$goalTitle",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpilogueBody(
    text: String,
    paragraphs: List<String>,
    writing: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.copy(alpha = 0.36f))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 17.dp, vertical = 16.dp),
    ) {
        Column {
            Text(
                "后日谈",
                style = MaterialTheme.typography.labelSmall,
                color = Amber.copy(alpha = 0.8f),
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(10.dp))
            paragraphs.forEach { paragraph ->
                Text(
                    paragraph,
                    style = GameTextStyle.copy(fontSize = 15.sp, lineHeight = 28.sp),
                    color = Parchment,
                    modifier = Modifier.padding(bottom = 11.dp),
                )
            }
            if (writing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▍", color = Amber, style = GameTextStyle)
                    Spacer(Modifier.width(8.dp))
                    AppSpinner(size = 13)
                }
            } else if (text.isNotBlank()) {
                Text(
                    "—— 完 ——",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}
