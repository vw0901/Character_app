@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ifitwereyou.character.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.ifitwereyou.character.data.BibleCharacter
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CharacterMode
import com.ifitwereyou.character.data.HistoryEntry
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.data.SaveState
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.SceneChoice
import com.ifitwereyou.character.game.characterKey
import com.ifitwereyou.character.game.characterKeyOfBook
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
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.components.appTextFieldColors
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.AmberLight
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.GameTextStyle
import com.ifitwereyou.character.ui.theme.Ink
import com.ifitwereyou.character.ui.theme.InkRaised
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
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
            onOpenStories = viewModel::openStoryPicker,
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
            onBackToStories = viewModel::backToStoryPicker,
            onRetryOpening = viewModel::retryOpening,
            onOpenSettings = viewModel::openSettings,
            onChoose = viewModel::choose,
            onSkipTyping = viewModel::skipTyping,
            onCustomTextChange = viewModel::updateCustomText,
            onPolish = viewModel::polishCustomAction,
            onRestoreCustom = viewModel::restoreCustomText,
            onOpenEpilogue = viewModel::openEpilogue,
            onRetryEnding = viewModel::retryEndingSummary,
            onRestartBook = viewModel::restartBook,
        )
    }
}

// ---------------------------------------------------------------------------
// 角色选择
// ---------------------------------------------------------------------------

@Composable
private fun CharacterSelectContent(
    document: ProjectDocument,
    ui: GameUiState,
    onOpenStories: (CharacterChoice) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onHome: () -> Unit,
    onOpenBible: () -> Unit,
) {
    val bible = document.bible
    val bibleCharacters = bible?.characters.orEmpty()
    val bibleChoices = bibleCharacters.map { existingChoice(it) }
    val optionChoices = document.storyOptions.keys.mapNotNull { key ->
        when {
            key.startsWith("custom:") -> CharacterChoice(
                mode = CharacterMode.CUSTOM,
                name = key.removePrefix("custom:"),
                background = "一位刚刚踏入小说世界的旅人",
            )
            key.startsWith("bible:") -> CharacterChoice(
                mode = CharacterMode.EXISTING,
                name = key.removePrefix("bible:"),
                background = "故事中的关键人物",
            )
            else -> null
        }
    }
    val customChoices = (
        document.adventures.values.map { it.character }.filter { it.mode == CharacterMode.CUSTOM } +
            optionChoices.filter { it.mode == CharacterMode.CUSTOM }
        ).distinctBy { characterKey(it) }
    val knownKeys = (bibleChoices + customChoices).map { characterKey(it) }.toSet()
    val legacyChoices = (
        document.adventures.values.map { it.character } +
            optionChoices.filter { it.mode == CharacterMode.EXISTING }
        )
        .filter { characterKey(it) !in knownKeys }
        .distinctBy { characterKey(it) }

    var mode by rememberSaveable { mutableStateOf("existing") }
    var customName by rememberSaveable { mutableStateOf("") }
    var customBackground by rememberSaveable { mutableStateOf("") }
    var customAbilities by rememberSaveable { mutableStateOf("") }
    var customPersonality by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(
        title = "选择故事角色",
        subtitle = "先选角色，再从 TA 的故事栏里挑一条命运",
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
        if (bibleCharacters.isNotEmpty()) {
            Text(
                "从小说设定中挑选一位人物。选好后，AI 会为 TA 构思多条互不相同的命运。",
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
            if (bibleCharacters.isEmpty()) {
                CardBox(backgroundColor = Card) {
                    Text(
                        "设定圣经中暂未提取到人物，可直接自创角色进入故事。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bibleChoices.forEach { choice ->
                        val bibleCharacter = bibleCharacters.firstOrNull { it.name == choice.name }
                        CharacterCard(
                            choice = choice,
                            subtitle = bibleCharacter?.role.orEmpty().ifBlank { "故事中的关键人物" },
                            document = document,
                            onClick = { onOpenStories(choice) },
                        )
                    }
                }
            }
        } else {
            CardBox(backgroundColor = Card) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "创造一位只属于你的角色。选好后同样会生成专属故事栏。",
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
                        text = "进入 TA 的故事栏",
                        icon = Icons.Outlined.AutoStories,
                        enabled = customName.isNotBlank() && !ui.isInitializing && !ui.isGeneratingStories,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onOpenStories(
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

        if (customChoices.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            SectionLabel(icon = Icons.Outlined.Person, title = "我的角色", hint = "自创角色")
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                customChoices.forEach { choice ->
                    CharacterCard(
                        choice = choice,
                        subtitle = "自创角色",
                        document = document,
                        onClick = { onOpenStories(choice) },
                    )
                }
            }
        }

        if (legacyChoices.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            SectionLabel(icon = Icons.Outlined.Book, title = "旧日角色", hint = "旧存档里的旅程")
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                legacyChoices.forEach { choice ->
                    CharacterCard(
                        choice = choice,
                        subtitle = "旧存档角色",
                        document = document,
                        onClick = { onOpenStories(choice) },
                    )
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
                    "每个角色可以有多条故事线，每条命运独立保存进度；回到故事栏不会丢失任何进度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.width(8.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Muted.copy(alpha = 0.6f))
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
    choice: CharacterChoice,
    subtitle: String,
    document: ProjectDocument,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val charKey = characterKey(choice)
    val books = document.adventures.filterKeys { characterKeyOfBook(it) == charKey }.values.toList()
    val storyCount = document.storyOptions[charKey].orEmpty().size
    val playing = books.firstOrNull { it.outcome == AdventureOutcome.PLAYING }
    val finished = books.count { it.outcome != AdventureOutcome.PLAYING }

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
                    Text(choice.name, style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Amber)
                }
                Chip(
                    text = when {
                        playing != null -> "继续"
                        books.isNotEmpty() -> "有结局"
                        storyCount > 0 -> "可选"
                        else -> "新故事"
                    },
                    color = when {
                        playing != null -> Amber
                        books.isNotEmpty() -> Muted
                        storyCount > 0 -> Forest
                        else -> Forest
                    },
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        storyCount > 0 -> "$storyCount 条故事线"
                        else -> "进入故事栏后构思故事线"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                if (books.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "· ${books.size} 段已开启${if (finished > 0) " / $finished 段完结" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted.copy(alpha = 0.8f),
                    )
                }
            }
            if (playing != null) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "上次停在：第 ${playing.timeline.currentDay}/${playing.timeline.totalDays} 天 · 生命 ${playing.stats.health}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber.copy(alpha = 0.85f),
                )
            }
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

// ---------------------------------------------------------------------------
// 冒险进行中
// ---------------------------------------------------------------------------

@Composable
private fun GamePlayContent(
    document: ProjectDocument,
    book: AdventureState,
    ui: GameUiState,
    saveState: SaveState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenBible: () -> Unit,
    onUndo: () -> Unit,
    onExport: () -> Unit,
    onSwitchCharacter: () -> Unit,
    onBackToStories: () -> Unit,
    onRetryOpening: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoose: (String, String) -> Unit,
    onSkipTyping: () -> Unit,
    onCustomTextChange: (String) -> Unit,
    onPolish: () -> Unit,
    onRestoreCustom: () -> Unit,
    onOpenEpilogue: () -> Unit,
    onRetryEnding: () -> Unit,
    onRestartBook: () -> Unit,
) {
    val game = book.gameState
    val completed = book.outcome == AdventureOutcome.SUCCESS
    val canUndo = book.snapshots.isNotEmpty() && !ui.isProcessing && !completed
    val typingNow = ui.isTyping || (ui.narration.isNotEmpty() && ui.displayedText != ui.narration)
    val ended = book.outcome != AdventureOutcome.PLAYING
    val turnLabel = if (book.turn == 0) "序章" else "第 ${book.turn} 回合"
    val title = book.story?.title?.takeIf { it.isNotBlank() }
        ?: game?.title?.takeIf { it.isNotBlank() }
        ?: document.novelName.ifBlank { "文字冒险" }
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }

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

        if (game == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (ui.isInitializing) {
                    LoadingBlock(
                        title = "正在生成冒险世界…",
                        subtitle = "以 ${book.character.name} 的身份，走进「${book.story?.title ?: "这段故事"}」",
                    )
                } else if (ui.error.isNotBlank()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InlineError(ui.error, onOpenSettings = onOpenSettings)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = "重新生成开场",
                                icon = Icons.Outlined.Refresh,
                                onClick = onRetryOpening,
                            )
                            GhostButton(text = "更换故事", onClick = onBackToStories)
                        }
                    }
                } else {
                    Text("故事即将开始…", color = Muted)
                }
            }
        } else {
            AdventureScrollContent(
                book = book,
                ui = ui,
                saveState = saveState,
                typingNow = typingNow,
                ended = ended,
                modifier = Modifier.weight(1f),
                onSkipTyping = onSkipTyping,
                onChoose = onChoose,
                onOpenGoal = { showGoalDialog = true },
                onUndo = onUndo,
                canUndo = canUndo,
                onOpenEpilogue = onOpenEpilogue,
                onRetryEnding = onRetryEnding,
                onRestartBook = onRestartBook,
                onBackToStories = onBackToStories,
            )
            AdventureDock(
                book = book,
                ui = ui,
                enabled = !ui.busy && !typingNow && !ended,
                onOpenGoal = { showGoalDialog = true },
                onCustomAction = { showCustomSheet = true },
            )
        }
    }

    if (showGoalDialog && game != null) {
        GoalDialog(
            book = book,
            onDismiss = { showGoalDialog = false },
        )
    }

    if (showCustomSheet) {
        CustomActionSheet(
            ui = ui,
            enabled = !ui.busy && !typingNow && !ended,
            onDismiss = { showCustomSheet = false },
            onTextChange = onCustomTextChange,
            onPolish = onPolish,
            onRestore = onRestoreCustom,
            onSubmit = {
                val text = ui.customText.trim()
                if (text.isNotEmpty()) {
                    onChoose("custom_${book.turn}", text)
                    showCustomSheet = false
                }
            },
        )
    }
}

@Composable
private fun AdventureScrollContent(
    book: AdventureState,
    ui: GameUiState,
    saveState: SaveState,
    typingNow: Boolean,
    ended: Boolean,
    modifier: Modifier = Modifier,
    onSkipTyping: () -> Unit,
    onChoose: (String, String) -> Unit,
    onOpenGoal: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onOpenEpilogue: () -> Unit,
    onRetryEnding: () -> Unit,
    onRestartBook: () -> Unit,
    onBackToStories: () -> Unit,
) {
    val game = book.gameState ?: return
    val listState = rememberLazyListState()
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    val history = book.history
    val narrationIndex = history.indexOfLast { it.type == HistoryType.NARRATION }
    val previous = if (narrationIndex >= 0) history.take(narrationIndex) else history
    val currentNarration = when {
        ui.narration.isBlank() -> history.getOrNull(narrationIndex)?.text.orEmpty()
        ui.isTyping -> ui.displayedText
        else -> ui.narration
    }
    val location = game.currentScene.location

    LaunchedEffect(book.turn) {
        historyExpanded = false
        listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "history-toggle") {
            HistoryToggle(
                expanded = historyExpanded,
                count = previous.size,
                saveState = saveState,
                onToggle = { historyExpanded = !historyExpanded },
            )
            AnimatedVisibility(
                visible = historyExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HistoryPanel(entries = previous)
                }
            }
        }

        if (!ended) {
            item(key = "current-turn") {
                CurrentTurnCard(
                    turn = book.turn,
                    location = location,
                    text = currentNarration,
                    typing = typingNow,
                    processing = ui.isProcessing,
                    onSkipTyping = onSkipTyping,
                )
            }

            if (ui.error.isNotBlank() && !ui.isProcessing) {
                item(key = "error") {
                    InlineError(ui.error, onOpenSettings = null)
                }
            }

            if (!ui.isProcessing && !typingNow) {
                item(key = "choices") {
                    ChoiceList(
                        scene = game.currentScene,
                        onChoice = onChoose,
                    )
                }
                item(key = "custom-hint") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = Muted.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "也可以点下方「自述行动」，写下你自己的选择。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        } else {
            item(key = "outcome") {
                OutcomePanel(
                    book = book,
                    ui = ui,
                    canUndo = canUndo,
                    onUndo = onUndo,
                    onOpenEpilogue = onOpenEpilogue,
                    onRetryEnding = onRetryEnding,
                    onRestartBook = onRestartBook,
                    onBackToStories = onBackToStories,
                )
            }
        }

        if (ended) {
            item(key = "goal-link") {
                TextButton(onClick = onOpenGoal) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("查看本局目标与时间线", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun HistoryToggle(
    expanded: Boolean,
    count: Int,
    saveState: SaveState,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Card.copy(alpha = 0.75f))
            .border(1.dp, Border, RoundedCornerShape(7.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("故事历史", style = MaterialTheme.typography.labelMedium, color = Parchment)
        Spacer(Modifier.width(7.dp))
        Text(
            if (count > 0) "之前的 $count 条前情" else "还没有前情",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        Spacer(Modifier.weight(1f))
        if (saveState == SaveState.SAVING) {
            SaveStateText(saveState)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (expanded) "收起" else "展开",
            style = MaterialTheme.typography.labelSmall,
            color = Amber.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (expanded) "收起故事历史" else "展开故事历史",
            tint = Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HistoryPanel(entries: List<HistoryEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Card.copy(alpha = 0.55f))
            .border(1.dp, Border.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (entries.isEmpty()) {
            Text(
                "故事刚开始，还没有可回看的前情。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        } else {
            entries.forEach { entry ->
                if (entry.type == HistoryType.NARRATION) {
                    Text(
                        entry.text,
                        style = GameTextStyle.copy(fontSize = 13.sp, lineHeight = 22.sp),
                        color = Muted,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .background(Amber.copy(alpha = 0.35f)),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            entry.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTurnCard(
    turn: Int,
    location: String,
    text: String,
    typing: Boolean,
    processing: Boolean,
    onSkipTyping: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Secondary.copy(alpha = 0.95f), Card),
                ),
            )
            .border(1.dp, Amber.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 17.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(Amber, CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (turn == 0) "序章 · 此刻" else "第 $turn 回合 · 此刻",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber.copy(alpha = 0.9f),
                )
                if (location.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        location,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            if (text.isBlank()) {
                if (processing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSpinner(size = 16)
                        Spacer(Modifier.width(9.dp))
                        Text("命运正在编织这一回合…", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                } else {
                    Text("故事即将开始…", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            } else {
                Text(
                    text = text,
                    style = GameTextStyle.copy(fontSize = 16.sp, lineHeight = 30.sp),
                    color = Parchment,
                )
            }
            if (typing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onSkipTyping) {
                        Text("显示全文", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
            if (processing && text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Border.copy(alpha = 0.8f))
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSpinner(size = 14)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "命运正在编织下一回合…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("你打算…", style = MaterialTheme.typography.titleSmall, color = Parchment)
            Spacer(Modifier.weight(1f))
            Text(
                "${scene.choices.size} 个选择",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        scene.choices.forEachIndexed { index, choice ->
            ChoiceCard(index = index, choice = choice, onClick = onChoice)
        }
    }
}

@Composable
private fun ChoiceCard(
    index: Int,
    choice: SceneChoice,
    onClick: (String, String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Card)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick(choice.id, choice.text)
            }
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Amber.copy(alpha = 0.10f), CircleShape)
                    .border(1.dp, Amber.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(choice.text, style = MaterialTheme.typography.bodyMedium, color = Parchment)
                if (choice.hint.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        choice.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Amber.copy(alpha = 0.7f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 底部状态坞：生命条 + 目标 + 自述行动
// ---------------------------------------------------------------------------

@Composable
private fun AdventureDock(
    book: AdventureState,
    ui: GameUiState,
    enabled: Boolean,
    onOpenGoal: () -> Unit,
    onCustomAction: () -> Unit,
) {
    val health = book.stats.health
    val status = when {
        health <= 0 -> "力竭"
        health <= 30 -> "濒危"
        health <= 60 -> "带伤"
        else -> "尚稳"
    }
    val healthColor = when {
        health <= 30 -> Crimson
        health <= 60 -> Amber
        else -> Color(0xFFB6534B)
    }
    val remaining = daysRemaining(book.timeline)
    val urgent = remaining <= 3 && book.outcome == AdventureOutcome.PLAYING

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = InkRaised,
        shadowElevation = 18.dp,
    ) {
        Column {
            HorizontalDivider(color = Border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Favorite, contentDescription = null, tint = healthColor, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("生命", style = MaterialTheme.typography.labelMedium, color = Parchment)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "$health",
                        style = MaterialTheme.typography.titleSmall,
                        color = healthColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " / 100 · $status",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = if (urgent) Crimson else Muted,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "第 ${book.timeline.currentDay}/${book.timeline.totalDays} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (urgent) Crimson else Muted,
                    )
                }
                Spacer(Modifier.height(7.dp))
                HealthBar(health = health, color = healthColor)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onOpenGoal)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Flag, contentDescription = null, tint = Amber, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            book.goal.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Parchment.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Amber.copy(alpha = if (enabled) 0.14f else 0.06f))
                            .border(
                                1.dp,
                                Amber.copy(alpha = if (enabled) 0.40f else 0.15f),
                                RoundedCornerShape(7.dp),
                            )
                            .clickable(enabled = enabled, onClick = onCustomAction)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = if (enabled) Amber else Muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (ui.isProcessing) "推进中" else "自述行动",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) Amber else Muted.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthBar(health: Int, color: Color) {
    val target = health.coerceIn(0, 100) / 100f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "health",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(CircleShape)
            .background(Secondary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(9.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.82f), if (health <= 30) Crimson else AmberLight),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            for (index in 1..9) {
                val x = size.width * index / 10f
                drawLine(
                    color = Ink.copy(alpha = 0.55f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 目标详情 / 自述行动
// ---------------------------------------------------------------------------

@Composable
private fun GoalDialog(
    book: AdventureState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        titleContentColor = Parchment,
        textContentColor = Muted,
        shape = RoundedCornerShape(10.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("本局目标", style = MaterialTheme.typography.titleMedium, color = Parchment)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (!book.story?.title.isNullOrBlank()) {
                    Text(
                        "故事 · ${book.story?.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber.copy(alpha = 0.85f),
                    )
                }
                Text(book.goal.title, style = MaterialTheme.typography.titleSmall, color = Parchment)
                if (book.goal.description.isNotBlank()) {
                    Text(book.goal.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                HorizontalDivider(color = Border)
                Text(
                    "${book.timeline.label} · 第 ${book.timeline.currentDay} / ${book.timeline.totalDays} 天" +
                        " · 生命 ${book.stats.health}/100",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Text(
                    "故事线：${book.story?.summary.orEmpty().ifBlank { "旧存档故事" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", style = MaterialTheme.typography.labelMedium, color = Amber)
            }
        },
    )
}

@Composable
private fun CustomActionSheet(
    ui: GameUiState,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onPolish: () -> Unit,
    onRestore: () -> Unit,
    onSubmit: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmed = ui.customText.trim()
    val canPolish = trimmed.length >= 2 && !ui.isPolishing
    val canSubmit = trimmed.isNotEmpty() && !ui.isPolishing && enabled

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InkRaised,
        contentColor = Parchment,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Border),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Amber, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("自述行动", style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Text(
                        "写下你此刻真正想做的事，故事会认真回应它。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = ui.customText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !ui.isPolishing,
                minLines = 3,
                maxLines = 6,
                placeholder = {
                    Text(
                        "例如：我压低声音，问斗笠人是不是守桥人。",
                        color = Muted.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ui.customText.length}/200",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                if (ui.originalCustomText.isNotEmpty() && !ui.isPolishing) {
                    Spacer(Modifier.width(12.dp))
                    CompactAction(text = "还原原句", onClick = onRestore)
                }
                Spacer(Modifier.weight(1f))
            }
            if (ui.isPolishing) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSpinner(size = 13)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "AI 正在把你的行动写进故事里…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            if (ui.polishError.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(ui.polishError, style = MaterialTheme.typography.labelSmall, color = Crimson)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton(
                    text = if (ui.isPolishing) "润色中…" else "润色",
                    icon = if (ui.isPolishing) null else Icons.Outlined.Edit,
                    enabled = canPolish && enabled,
                    modifier = Modifier.weight(1f),
                    onClick = onPolish,
                )
                EmberButton(
                    text = "开始行动",
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f),
                    onClick = onSubmit,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 结局
// ---------------------------------------------------------------------------

@Composable
private fun OutcomePanel(
    book: AdventureState,
    ui: GameUiState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpenEpilogue: () -> Unit,
    onRetryEnding: () -> Unit,
    onRestartBook: () -> Unit,
    onBackToStories: () -> Unit,
) {
    val success = book.outcome == AdventureOutcome.SUCCESS
    val summaryText = when {
        ui.isWritingEnding && ui.endingDraft.isNotBlank() -> ui.endingDraft
        else -> book.endingSummary
    }
    val paragraphs = storyParagraphs(summaryText)
    val closingText = book.endingText.ifBlank {
        book.history.lastOrNull { it.type == HistoryType.NARRATION }?.text.orEmpty()
    }
    val epilogueReady = book.epilogue.isNotBlank()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = if (success) Icons.Outlined.Star else Icons.Outlined.Warning,
            contentDescription = null,
            tint = if (success) Amber else Crimson,
            modifier = Modifier.size(52.dp),
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
                if (success) "最终目标：${book.goal.title}" else book.outcomeReason,
                style = MaterialTheme.typography.bodySmall,
                color = if (success) Forest else Crimson,
            )
        }
        if (closingText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                closingText,
                style = GameTextStyle.copy(fontSize = 14.sp, lineHeight = 25.sp),
                color = Parchment.copy(alpha = 0.9f),
            )
        }

        Spacer(Modifier.height(20.dp))
        CardBox(
            backgroundColor = Card.copy(alpha = 0.8f),
            borderColor = Amber.copy(alpha = 0.28f),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("结局页 · 故事总结", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.weight(1f))
                    if (!ui.isWritingEnding && ui.endingError.isNotBlank()) {
                        TextButton(onClick = onRetryEnding) {
                            Text("重新生成", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
                if (paragraphs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    paragraphs.forEach { paragraph ->
                        Text(
                            paragraph,
                            style = GameTextStyle.copy(fontSize = 14.sp, lineHeight = 25.sp),
                            color = Parchment,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                    if (ui.isWritingEnding) {
                        Text("▍", color = Amber, style = GameTextStyle)
                    }
                }
                if (!ui.isWritingEnding && ui.endingError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    InlineError(ui.endingError, onOpenSettings = null)
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = "重新生成结局页",
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetryEnding,
                    )
                }
                if (paragraphs.isEmpty() && !ui.isWritingEnding && ui.endingError.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSpinner(size = 15)
                        Spacer(Modifier.width(8.dp))
                        Text("正在为这段故事写结局总结…", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "结局页写到故事停下的一刻；后日谈是可选的，等你准备好了再继续。",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(18.dp))
        FlowRowButtons {
            if (!success) {
                GhostButton(
                    text = "回退一步",
                    enabled = canUndo,
                    onClick = onUndo,
                )
            }
            GhostButton(
                text = "重开本故事",
                icon = Icons.Outlined.Refresh,
                onClick = onRestartBook,
            )
            EmberButton(
                text = if (epilogueReady) "阅读后日谈" else "生成后日谈",
                onClick = onOpenEpilogue,
            )
            GhostButton(
                text = "选择其他故事",
                onClick = onBackToStories,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun FlowRowButtons(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

internal fun storyParagraphs(text: String): List<String> {
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
