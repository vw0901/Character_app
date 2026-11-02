@file:OptIn(ExperimentalMaterial3Api::class)

package com.ifitwereyou.character.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CharacterMode
import com.ifitwereyou.character.data.StoryOption
import com.ifitwereyou.character.game.characterKey
import com.ifitwereyou.character.game.storyKey
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.GameUiState
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.AppTopBar
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary

@Composable
fun StoryPickerScreen(
    viewModel: AppViewModel,
    onExport: () -> Unit,
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val ui by viewModel.gameState.collectAsStateWithLifecycle()
    val document = project ?: return
    val character = ui.pickerCharacter ?: return

    val charKey = characterKey(character)
    val options = document.storyOptions[charKey].orEmpty()
    val legacyBooks = document.adventures.filterKeys { key ->
        key == charKey || key.substringBefore('#') == charKey && key.substringAfter('#', "") !in options.map { it.id }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "故事栏",
            subtitle = "扮演 · ${character.name}",
            onBack = viewModel::closeStoryPicker,
            actions = {
                TopBarIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = "导出存档",
                    onClick = onExport,
                )
                OverflowMenu(
                    items = listOf(
                        MenuAction("查看设定圣经", onClick = viewModel::openBible),
                        MenuAction("AI 设置", onClick = viewModel::openSettings),
                    ),
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CharacterHero(character = character, bibleTone = document.bible?.tone.orEmpty())
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("故事栏", style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Spacer(Modifier.width(8.dp))
                    if (options.isNotEmpty()) {
                        Text(
                            "${options.size} 条命运",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber.copy(alpha = 0.8f),
                        )
                    }
                }
                Text(
                    "同一个角色的不同命运，选一条开始；每条故事各自保存进度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (ui.isGeneratingStories) {
                item { StoryGeneratingBlock(character.name) }
            }
            if (ui.storyError.isNotBlank() && !ui.isGeneratingStories) {
                item {
                    Column {
                        InlineError(ui.storyError, onOpenSettings = viewModel::openSettings)
                        Spacer(Modifier.height(10.dp))
                        GhostButton(
                            text = "重新构思故事栏",
                            icon = Icons.Outlined.Refresh,
                            onClick = viewModel::retryStoryOptions,
                        )
                    }
                }
            }

            itemsIndexed(options, key = { _, story -> story.id }) { index, story ->
                val book = document.adventures[storyKey(character, story.id)]
                StoryCard(
                    index = index,
                    story = story,
                    book = book,
                    onClick = { viewModel.startStory(character, story) },
                    onEpilogue = {
                        viewModel.openEpilogueForBook(storyKey(character, story.id))
                    },
                )
            }

            if (legacyBooks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Book, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("旧日故事", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "这个角色在旧存档里的旅程",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted.copy(alpha = 0.6f),
                        )
                    }
                }
                items(legacyBooks.entries.toList(), key = { it.key }) { (key, book) ->
                    LegacyStoryCard(
                        book = book,
                        onClick = { viewModel.openBook(key) },
                        onEpilogue = { viewModel.openEpilogueForBook(key) },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Card.copy(alpha = 0.35f))
                        .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "故事线由 AI 依据设定圣经构思；每条故事各自保存进度，重开故事也不会影响其他命运。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterHero(character: CharacterChoice, bibleTone: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Secondary, Card, Card.copy(alpha = 0.9f)),
                ),
            )
            .border(1.dp, Amber.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Amber.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, Amber.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        character.name.take(1),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Amber,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge, color = Parchment)
                    Text(
                        if (character.mode == CharacterMode.CUSTOM) "自创角色" else "原著角色",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber.copy(alpha = 0.8f),
                    )
                }
                Chip(text = "第一人称", color = Forest)
            }
            if (character.background.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    character.background,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (bibleTone.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Border.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "原著文风 · $bibleTone",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber.copy(alpha = 0.72f),
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StoryGeneratingBlock(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Card.copy(alpha = 0.55f))
                    .border(1.dp, Border, RoundedCornerShape(9.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "0${index + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Amber.copy(alpha = 0.22f),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.52f)
                            .height(13.dp)
                            .clip(CircleShape)
                            .background(Secondary),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.86f)
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(Secondary.copy(alpha = 0.7f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.64f)
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(Secondary.copy(alpha = 0.7f)),
                    )
                }
                AppSpinner(size = 18)
            }
        }
        Text(
            "正在为 $name 推演不同命运的入口…",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun StoryCard(
    index: Int,
    story: StoryOption,
    book: AdventureState?,
    onClick: () -> Unit,
    onEpilogue: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val playing = book?.outcome == AdventureOutcome.PLAYING
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Card)
            .border(
                1.dp,
                if (playing) Amber.copy(alpha = 0.5f) else Border,
                RoundedCornerShape(10.dp),
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "0${index + 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Amber.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        story.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Parchment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        story.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (playing) {
                    Spacer(Modifier.width(8.dp))
                    Chip(text = "继续", color = Amber)
                } else if (book != null) {
                    Spacer(Modifier.width(8.dp))
                    Chip(text = "已完结", color = Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    story.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                if (book != null) {
                    Text(
                        if (playing) {
                            "第 ${book.timeline.currentDay}/${book.timeline.totalDays} 天 · 生命 ${book.stats.health}"
                        } else {
                            "点按回看结局"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (playing) Amber else Muted,
                    )
                } else {
                    Text(
                        "开始这段命运",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                    )
                }
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (playing) Amber else Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (book != null && !playing) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    EpilogueAction(
                        label = if (book.epilogue.isBlank()) "生成后日谈" else "阅读后日谈",
                        onClick = onEpilogue,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpilogueAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Amber.copy(alpha = 0.10f))
            .border(1.dp, Amber.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AutoStories,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Amber)
    }
}

@Composable
private fun LegacyStoryCard(
    book: AdventureState,
    onClick: () -> Unit,
    onEpilogue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Card.copy(alpha = 0.6f))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Book, contentDescription = null, tint = Amber, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.goal.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "旧存档 · 第 ${book.timeline.currentDay}/${book.timeline.totalDays} 天 · 生命 ${book.stats.health}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (book.outcome) {
                    AdventureOutcome.PLAYING -> "继续"
                    AdventureOutcome.SUCCESS -> "已达成"
                    AdventureOutcome.FAILURE -> "已失败"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (book.outcome == AdventureOutcome.PLAYING) Amber else Muted,
            )
            if (book.outcome != AdventureOutcome.PLAYING) {
                Spacer(Modifier.height(5.dp))
                EpilogueAction(
                    label = if (book.epilogue.isBlank()) "后日谈" else "阅读后日谈",
                    onClick = onEpilogue,
                )
            }
        }
        Spacer(Modifier.width(3.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
    }
}
