@file:OptIn(ExperimentalLayoutApi::class)

package com.ifitwereyou.character.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.BibleCharacter
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CharacterMode
import com.ifitwereyou.character.data.GameState
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryTimeline
import com.ifitwereyou.character.game.characterKey
import com.ifitwereyou.character.game.daysRemaining
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.GameUiState
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.AppTopBar
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.CompactAction
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.LoadingBlock
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.SaveStateText
import com.ifitwereyou.character.ui.components.ScreenScaffold
import com.ifitwereyou.character.ui.components.StatMeter
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.components.appTextFieldColors
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.GameTextStyle
import com.ifitwereyou.character.ui.theme.Ink
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Sage
import com.ifitwereyou.character.ui.theme.Secondary

@Composable
fun GameScreen(
    viewModel: AppViewModel,
    onExport: () -> Unit,
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val ui by viewModel.gameState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    val document = project
    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在打开故事书…", color = Muted)
        }
        return
    }

    val book = document.activeAdventureKey?.let { document.adventures[it] }
    if (book == null) {
        CharacterSelectContent(
            document = document,
            ui = ui,
            saveState = saveState,
            onStartBook = viewModel::startBook,
            onOpenBook = viewModel::openBook,
            onExport = onExport,
            onBack = { viewModel.handleBack() },
            onOpenSettings = viewModel::openSettings,
            onHome = viewModel::goHome,
            onOpenBible = viewModel::openBible,
        )
    } else {
        GamePlayContent(
            document = document,
            book = book,
            ui = ui,
            saveState = saveState,
            onBack = { viewModel.handleBack() },
            onHome = viewModel::goHome,
            onOpenBible = viewModel::openBible,
            onUndo = viewModel::undo,
            onExport = onExport,
            onSwitchCharacter = { viewModel.leaveBook() },
            onLeave = { viewModel.leaveBook() },
            onRetryOpening = viewModel::retryOpening,
            onOpenSettings = viewModel::openSettings,
            onChoose = viewModel::choose,
            onSkipTyping = viewModel::skipTyping,
            onCustomTextChange = viewModel::updateCustomText,
            onPolish = viewModel::polishCustomAction,
            onRestoreCustom = viewModel::restoreCustomText,
            onRewriteEpilogue = viewModel::rewriteEpilogue,
            onRestartBook = viewModel::restartBook,
        )
    }
}

// ---------------------------------------------------------------------------
// 角色选择 / 书架
// ---------------------------------------------------------------------------

@Composable
private fun CharacterSelectContent(
    document: ProjectDocument,
    ui: GameUiState,
    saveState: com.ifitwereyou.character.data.SaveState,
    onStartBook: (CharacterChoice) -> Unit,
    onOpenBook: (String) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onHome: () -> Unit,
    onOpenBible: () -> Unit,
) {
    val bible = document.bible
    val characters = bible?.characters.orEmpty()
    val coreKeys = characters.map { characterKey(existingChoice(it)) }.toSet()
    val shelfBooks = document.adventures.filterKeys { it !in coreKeys }

    var mode by rememberSaveable { mutableStateOf("existing") }
    var customName by rememberSaveable { mutableStateOf("") }
    var customBackground by rememberSaveable { mutableStateOf("") }
    var customAbilities by rememberSaveable { mutableStateOf("") }
    var customPersonality by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(
        title = "选择故事书",
        subtitle = "每个角色一本独立故事书 · ${saveState.label}",
        onBack = onBack,
        actions = {
            TopBarIcon(
                icon = Icons.Outlined.Share,
                contentDescription = "导出存档",
                onClick = onExport,
            )
            OverflowMenu(
                items = listOf(
                    MenuAction("查看设定圣经", onClick = onOpenBible),
                    MenuAction("返回书架", onClick = onHome),
                    MenuAction("AI 设置", onClick = onOpenSettings),
                ),
            )
        },
    ) {
        if (!characters.isEmpty()) {
            Text(
                "从小说设定中挑选一位人物，以第一人称进入属于 TA 的故事。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(Modifier.height(14.dp))
        }
        if (!bible?.tone.isNullOrBlank()) {
            Text(
                "原著文风：${bible?.tone}",
                style = MaterialTheme.typography.labelSmall,
                color = Amber.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeButton(
                text = "扮演故事角色",
                selected = mode == "existing",
                modifier = Modifier.weight(1f),
                onClick = { mode = "existing" },
            )
            ModeButton(
                text = "自创角色",
                selected = mode == "custom",
                modifier = Modifier.weight(1f),
                onClick = { mode = "custom" },
            )
        }
        Spacer(Modifier.height(18.dp))

        if (mode == "existing") {
            if (characters.isEmpty()) {
                CardBox(backgroundColor = Card) {
                    Text(
                        "设定圣经中暂未提取到人物，可直接自创角色进入故事。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    characters.forEach { character ->
                        val key = characterKey(existingChoice(character))
                        CharacterCard(
                            character = character,
                            book = document.adventures[key],
                            onClick = { onStartBook(existingChoice(character)) },
                        )
                    }
                }
            }
        } else {
            CardBox(backgroundColor = Card) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "创造一位只属于你的角色。同名角色会继续已有的故事书。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("角色名字 *") },
                        placeholder = { Text("例如：云中客") },
                        colors = appTextFieldColors(),
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = customBackground,
                        onValueChange = { customBackground = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("角色背景") },
                        placeholder = { Text("例如：一位从异世界误入此处的流浪旅人，对这个世界一无所知…") },
                        colors = appTextFieldColors(),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = customAbilities,
                            onValueChange = { customAbilities = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("能力 / 特长（可选）") },
                            placeholder = { Text("通晓古文字") },
                            colors = appTextFieldColors(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        OutlinedTextField(
                            value = customPersonality,
                            onValueChange = { customPersonality = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("性格（可选）") },
                            placeholder = { Text("谨慎、好奇") },
                            colors = appTextFieldColors(),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    EmberButton(
                        text = "打开 TA 的故事书",
                        icon = Icons.Outlined.Add,
                        enabled = customName.isNotBlank() && !ui.isInitializing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onStartBook(
                                CharacterChoice(
                                    mode = CharacterMode.CUSTOM,
                                    name = customName.trim(),
                                    background = customBackground.trim()
                                        .ifBlank { "一位刚刚踏入小说世界的旅人" },
                                    abilities = customAbilities.trim().takeIf { it.isNotBlank() },
                                    personality = customPersonality.trim().takeIf { it.isNotBlank() },
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (shelfBooks.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Book, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("书架", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.width(8.dp))
                Text(
                    "自创角色与不在设定中的人物",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shelfBooks.forEach { (key, book) ->
                    CardBox(
                        backgroundColor = Card.copy(alpha = 0.65f),
                        onClick = { onOpenBook(key) },
                        padding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Book, contentDescription = null, tint = Amber, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(book.character.name, style = MaterialTheme.typography.bodyMedium, color = Parchment)
                                Text(book.goal.title, style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                            Text(bookSummary(book), style = MaterialTheme.typography.labelSmall, color = Muted)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        CardBox(
            backgroundColor = Card.copy(alpha = 0.35f),
            borderColor = Border.copy(alpha = 0.7f),
            padding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "每个角色一本故事书，进度会自动保存在本机；返回书架不会丢失任何角色进度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CardBox(
        modifier = modifier.heightIn(min = 48.dp),
        backgroundColor = if (selected) Amber else Card,
        borderColor = if (selected) Amber else Border,
        onClick = onClick,
        padding = PaddingValues(vertical = 11.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Ink else Muted,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CharacterCard(
    character: BibleCharacter,
    book: AdventureState?,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    CardBox(
        backgroundColor = Card,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        padding = PaddingValues(15.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Secondary, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Amber, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Text(
                        character.role.ifBlank { "关键人物" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                    )
                }
                Chip(
                    text = when {
                        book == null -> "新故事"
                        book.outcome == AdventureOutcome.PLAYING -> "继续"
                        else -> "已完结"
                    },
                    color = when {
                        book == null -> Forest
                        book.outcome == AdventureOutcome.PLAYING -> Amber
                        else -> Muted
                    },
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(19.dp))
            }
            if (character.abilities.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("能力：${character.abilities}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (character.personality.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(character.personality, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            }
            if (book != null) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(color = Border)
                Spacer(Modifier.height(8.dp))
                Text("最终目标：${book.goal.title}", style = MaterialTheme.typography.labelSmall, color = Amber)
                Spacer(Modifier.height(2.dp))
                Text(bookSummary(book), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                when {
                    book == null -> "点按扮演这位角色，开启新的故事书"
                    book.outcome == AdventureOutcome.PLAYING -> "点按继续这本故事书"
                    else -> "点按回看已完结的故事"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Amber.copy(alpha = 0.85f),
            )
        }
    }
}

private fun existingChoice(character: BibleCharacter): CharacterChoice {
    val background = listOfNotNull(
        character.role.takeIf { it.isNotBlank() }?.let { "身份：$it" },
        character.abilities.takeIf { it.isNotBlank() }?.let { "能力：$it" },
        character.relationships.takeIf { it.isNotBlank() }?.let { "关系：$it" },
        character.personality.takeIf { it.isNotBlank() }?.let { "性格：$it" },
    ).joinToString("；").ifBlank { "故事中的关键人物" }
    return CharacterChoice(
        mode = CharacterMode.EXISTING,
        name = character.name,
        background = background,
        abilities = character.abilities.takeIf { it.isNotBlank() },
        personality = character.personality.takeIf { it.isNotBlank() },
    )
}

private fun bookSummary(book: AdventureState): String =
    "第 ${book.timeline.currentDay}/${book.timeline.totalDays} 天 · 生命 ${book.stats.health} · ${outcomeLabel(book.outcome)}"

private fun outcomeLabel(outcome: AdventureOutcome): String = when (outcome) {
    AdventureOutcome.PLAYING -> "进行中"
    AdventureOutcome.SUCCESS -> "已达成目标"
    AdventureOutcome.FAILURE -> "已失败"
}

// ---------------------------------------------------------------------------
// 冒险进行中
// ---------------------------------------------------------------------------

@Composable
private fun GamePlayContent(
    document: ProjectDocument,
    book: AdventureState,
    ui: GameUiState,
    saveState: com.ifitwereyou.character.data.SaveState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenBible: () -> Unit,
    onUndo: () -> Unit,
    onExport: () -> Unit,
    onSwitchCharacter: () -> Unit,
    onLeave: () -> Unit,
    onRetryOpening: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoose: (String, String) -> Unit,
    onSkipTyping: () -> Unit,
    onCustomTextChange: (String) -> Unit,
    onPolish: () -> Unit,
    onRestoreCustom: () -> Unit,
    onRewriteEpilogue: () -> Unit,
    onRestartBook: () -> Unit,
) {
    val game = book.gameState
    val completed = book.outcome == AdventureOutcome.SUCCESS
    val canUndo = book.snapshots.isNotEmpty() && !ui.isProcessing && !completed
    val title = game?.title?.takeIf { it.isNotBlank() } ?: document.novelName.ifBlank { "文字冒险" }
    val typingNow = ui.isTyping || (ui.narration.isNotEmpty() && ui.displayedText != ui.narration)
    val ended = book.outcome != AdventureOutcome.PLAYING
    val turnLabel = if (book.turn == 0) "序章" else "第 ${book.turn} 回合"

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = title,
            subtitle = "扮演：${book.character.name} · $turnLabel",
            onBack = onBack,
            actions = {
                TopBarIcon(
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = "回退一步",
                    enabled = canUndo,
                    onClick = onUndo,
                )
                OverflowMenu(
                    items = listOf(
                        MenuAction("导出存档", onClick = onExport),
                        MenuAction(
                            label = "查看设定圣经",
                            enabled = document.bible != null,
                            onClick = onOpenBible,
                        ),
                        MenuAction("切换角色", enabled = !ui.busy, onClick = onSwitchCharacter),
                        MenuAction("返回书架", onClick = onHome),
                        MenuAction("AI 设置", onClick = onOpenSettings),
                    ),
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaveStateText(saveState)
                Spacer(Modifier.weight(1f))
                Text(
                    "可回退 ${if (completed) 0 else book.snapshots.size} 步",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.height(10.dp))

            StoryStatusCard(book.goal, book.timeline, book.outcome)

            if (ui.isInitializing) {
                LoadingBlock(
                    title = "正在生成冒险世界…",
                    subtitle = "以 ${book.character.name} 的身份，为 TA 定下最终目标与时间线",
                )
            } else if (game == null) {
                if (ui.error.isNotBlank()) {
                    InlineError(ui.error, onOpenSettings = onOpenSettings)
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = "重新生成开场",
                            icon = Icons.Outlined.Refresh,
                            onClick = onRetryOpening,
                        )
                        GhostButton(text = "前往 AI 设置", onClick = onOpenSettings)
                        GhostButton(text = "更换故事书", onClick = onSwitchCharacter)
                    }
                }
            } else {
                if (!ended) {
                    Spacer(Modifier.height(12.dp))
                    StatsCard(book.stats)
                }

                Spacer(Modifier.height(12.dp))
                GameLog(
                    book = book,
                    ui = ui,
                    typingNow = typingNow,
                    onSkipTyping = onSkipTyping,
                )

                if (ui.error.isNotBlank() && !ui.isProcessing) {
                    Spacer(Modifier.height(10.dp))
                    InlineError(ui.error, onOpenSettings = onOpenSettings)
                }

                if (ended) {
                    Spacer(Modifier.height(18.dp))
                    OutcomePanel(
                        book = book,
                        ui = ui,
                        canUndo = canUndo,
                        onUndo = onUndo,
                        onRewriteEpilogue = onRewriteEpilogue,
                        onRestartBook = onRestartBook,
                        onLeave = onLeave,
                    )
                } else if (!ui.isProcessing && !typingNow) {
                    Spacer(Modifier.height(14.dp))
                    ChoiceList(
                        scene = game.currentScene,
                        onChoice = onChoose,
                    )
                    Spacer(Modifier.height(12.dp))
                    CustomChoice(
                        ui = ui,
                        onTextChange = onCustomTextChange,
                        onPolish = onPolish,
                        onRestore = onRestoreCustom,
                        onSubmit = {
                            onChoose("custom_${book.turn}", ui.customText.trim())
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StoryStatusCard(
    goal: StoryGoal,
    timeline: StoryTimeline,
    outcome: AdventureOutcome,
) {
    val playing = outcome == AdventureOutcome.PLAYING
    val remaining = daysRemaining(timeline)
    val overdue = playing && timeline.currentDay > timeline.totalDays
    val urgent = playing && remaining <= 3
    val percent = (timeline.currentDay.toFloat() / timeline.totalDays.coerceAtLeast(1)).coerceIn(0f, 1f)

    CardBox(backgroundColor = Card) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("最终目标", style = MaterialTheme.typography.labelSmall, color = Amber)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.DateRange, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${timeline.label} · 第 ${timeline.currentDay} / ${timeline.totalDays} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) Crimson else Muted,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(goal.title, style = MaterialTheme.typography.titleSmall, color = Parchment)
            if (goal.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(goal.description, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Secondary, CircleShape),
                color = if (urgent) Crimson else Amber,
                trackColor = Secondary,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !playing -> "本局已结束"
                    overdue -> "已超时 ${timeline.currentDay - timeline.totalDays} 天"
                    remaining > 0 -> "剩余 $remaining 天"
                    else -> "最后一天"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (urgent || overdue) Crimson else Muted,
            )
        }
    }
}

@Composable
private fun StatsCard(stats: StatValues) {
    CardBox(backgroundColor = Card, padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            StatMeter(Icons.Outlined.Favorite, stats.health, Crimson)
            StatMeter(Icons.Outlined.Bolt, stats.power, Amber)
            StatMeter(Icons.Outlined.Star, stats.reputation, Sage)
        }
    }
}

@Composable
private fun GameLog(
    book: AdventureState,
    ui: GameUiState,
    typingNow: Boolean,
    onSkipTyping: () -> Unit,
) {
    val history = book.history
    val liveIndex = if (ui.narration.isNotBlank()) history.lastIndex else -1
    val listState = rememberLazyListState()

    LaunchedEffect(history.size, ui.displayedText, ui.isProcessing) {
        if (history.isEmpty() && !ui.isProcessing) return@LaunchedEffect
        val extra = if (ui.isProcessing) 1 else 0
        val target = (history.size + extra - 1).coerceAtLeast(0)
        listState.scrollToItem(target)
    }

    CardBox(backgroundColor = Card, padding = PaddingValues(16.dp)) {
        Column {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp, max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(history) { index, entry ->
                    if (entry.type == HistoryType.NARRATION) {
                        val text = if (index == liveIndex && (ui.isTyping || ui.displayedText.isNotEmpty())) {
                            ui.displayedText
                        } else {
                            entry.text
                        }
                        Text(
                            text = text,
                            style = GameTextStyle,
                            color = Parchment,
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(Amber.copy(alpha = 0.45f)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Amber, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = Amber,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        }
                    }
                }
                if (ui.isProcessing) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppSpinner(size = 15)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (ui.isVerifyingGoal) "正在判定本轮是否达成最终目标…" else "命运正在编织…",
                                style = GameTextStyle.copy(fontSize = 13.sp),
                                color = Muted,
                            )
                        }
                    }
                }
            }

            if (history.isEmpty() && !ui.isProcessing) {
                Text("故事即将开始…", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (typingNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onSkipTyping) {
                        Text("显示全文", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceList(
    scene: Scene,
    onChoice: (String, String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "当前位置: ${scene.location}",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        scene.choices.forEach { choice ->
            CardBox(
                backgroundColor = Card,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChoice(choice.id, choice.text)
                },
                padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Amber, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(choice.text, style = MaterialTheme.typography.bodyMedium, color = Parchment)
                        if (choice.hint.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(choice.hint, style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomChoice(
    ui: GameUiState,
    onTextChange: (String) -> Unit,
    onPolish: () -> Unit,
    onRestore: () -> Unit,
    onSubmit: () -> Unit,
) {
    val trimmed = ui.customText.trim()
    val canPolish = trimmed.length >= 2 && !ui.isPolishing
    val canSubmit = trimmed.isNotEmpty() && !ui.isPolishing

    CardBox(
        backgroundColor = Card.copy(alpha = 0.6f),
        borderColor = Muted.copy(alpha = 0.35f),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "或者，自己写一个行动（写好后可以让 AI 润色，也可以直接行动）",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.customText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.isPolishing,
                minLines = 2,
                maxLines = 4,
                placeholder = {
                    Text(
                        "用你自己的话写下此刻想做的事，例如：我压低声音，问斗笠人是不是守桥人。",
                        color = Muted.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ui.customText.length}/200",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                if (ui.originalCustomText.isNotEmpty() && !ui.isPolishing) {
                    CompactAction(text = "还原原句", onClick = onRestore)
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GhostButton(
                    text = if (ui.isPolishing) "润色中…" else "润色",
                    icon = if (ui.isPolishing) null else Icons.Outlined.Edit,
                    enabled = canPolish,
                    onClick = onPolish,
                )
                EmberButton(
                    text = "开始行动",
                    enabled = canSubmit,
                    onClick = onSubmit,
                )
            }
            if (ui.isPolishing) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "AI 正在把你的行动写成故事里的一句话…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            if (ui.polishError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(ui.polishError, style = MaterialTheme.typography.labelSmall, color = Crimson)
            }
        }
    }
}

@Composable
private fun OutcomePanel(
    book: AdventureState,
    ui: GameUiState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onRewriteEpilogue: () -> Unit,
    onRestartBook: () -> Unit,
    onLeave: () -> Unit,
) {
    val success = book.outcome == AdventureOutcome.SUCCESS
    val epilogueText = if (ui.isWritingEpilogue) ui.epilogueDraft else book.epilogue
    val paragraphs = epilogueParagraphs(epilogueText)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (success) Icons.Outlined.Star else Icons.Outlined.Warning,
            contentDescription = null,
            tint = if (success) Amber else Crimson,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (success) "达成最终目标" else "冒险失败",
            style = MaterialTheme.typography.headlineSmall,
            color = Parchment,
        )
        if (book.outcomeReason.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                book.outcomeReason,
                style = MaterialTheme.typography.bodySmall,
                color = if (success) Forest else Crimson,
            )
        }
        if (book.endingText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                book.endingText,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }

        Spacer(Modifier.height(18.dp))
        CardBox(backgroundColor = Card.copy(alpha = 0.65f), borderColor = Border) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = Amber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("后日谈 · 大结局", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.weight(1f))
                    if ((book.epilogue.isNotBlank() || ui.epilogueError.isNotBlank()) && !ui.isWritingEpilogue) {
                        TextButton(onClick = onRewriteEpilogue) {
                            Text(
                                if (book.epilogue.isNotBlank()) "重新书写" else "再试一次",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                    }
                }
                if (paragraphs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    paragraphs.forEach { paragraph ->
                        Text(
                            paragraph,
                            style = GameTextStyle.copy(fontSize = 14.sp, lineHeight = 24.sp),
                            color = Parchment,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (ui.isWritingEpilogue) {
                        Text("▍", color = Amber, style = GameTextStyle)
                    }
                }
                if (!ui.isWritingEpilogue && ui.epilogueError.isNotBlank()) {
                    Text(ui.epilogueError, style = MaterialTheme.typography.bodySmall, color = Crimson)
                }
                if (paragraphs.isEmpty() && ui.epilogueError.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSpinner(size = 15)
                        Spacer(Modifier.width(8.dp))
                        Text("正在为这段故事写下后日谈…", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }

        if (success) {
            Spacer(Modifier.height(12.dp))
            Text(
                "这段故事已经完结。想再体验一次，请回到书架选择其他角色。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }

        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatMeter(Icons.Outlined.Favorite, book.stats.health, Crimson)
            StatMeter(Icons.Outlined.Bolt, book.stats.power, Amber)
            StatMeter(Icons.Outlined.Star, book.stats.reputation, Sage)
        }

        Spacer(Modifier.height(18.dp))
        if (success) {
            EmberButton(text = "选择其他角色", onClick = onLeave)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "回退一步",
                    enabled = canUndo,
                    onClick = onUndo,
                )
                GhostButton(
                    text = "重开本角色",
                    icon = Icons.Outlined.Refresh,
                    onClick = onRestartBook,
                )
                EmberButton(text = "换一本故事书", onClick = onLeave)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun epilogueParagraphs(text: String): List<String> {
    val blocks = text.split(Regex("\\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (blocks.size <= 1) {
        return (blocks.firstOrNull().orEmpty())
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    return blocks
}
