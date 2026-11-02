@file:OptIn(ExperimentalLayoutApi::class)

package com.ifitwereyou.character.ui.screens

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.BibleCharacter
import com.ifitwereyou.character.data.ClassicQuote
import com.ifitwereyou.character.data.Faction
import com.ifitwereyou.character.data.IconicScene
import com.ifitwereyou.character.data.KeyItem
import com.ifitwereyou.character.data.KeyLocation
import com.ifitwereyou.character.data.Mystery
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.data.TimelineEvent
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.BibleUiState
import com.ifitwereyou.character.ui.components.AppTopBar
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.LoadingBlock
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.AmberLight
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.Ink
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Sage
import com.ifitwereyou.character.ui.theme.Secondary

private enum class BibleSection(val label: String) {
    OVERVIEW("总览"),
    WORLD("世界观"),
    POWER("力量体系"),
    PEOPLE("人物"),
    FACTIONS("势力"),
    STORY("故事线"),
}

@Composable
fun BibleScreen(
    viewModel: AppViewModel,
    onExport: () -> Unit,
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val state by viewModel.bibleState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    val document = project
    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在打开设定圣经…", color = Muted)
        }
        return
    }

    val bible = document.bible
    val novelTitle = document.search?.novelName?.ifBlank { document.novelName } ?: document.novelName
    var sectionName by rememberSaveable { mutableStateOf(BibleSection.OVERVIEW.name) }
    val section = BibleSection.entries.firstOrNull { it.name == sectionName } ?: BibleSection.OVERVIEW

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "设定圣经",
            subtitle = "《$novelTitle》 · ${saveState.label}",
            onBack = viewModel::goHome,
            actions = {
                TopBarIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = "导出存档",
                    onClick = onExport,
                )
                OverflowMenu(
                    items = listOf(
                        MenuAction("返回书架", onClick = viewModel::goHome),
                        MenuAction("AI 设置", onClick = viewModel::openSettings),
                    ),
                )
            },
        )

        BibleHero(
            bible = bible ?: SettingBible(novelName = novelTitle),
            novelTitle = novelTitle,
            generating = state.isGenerating,
        )

        if (bible == null) {
            BibleGenerationContent(
                document = document,
                state = state,
                onRetry = viewModel::retryBible,
                onOpenSettings = viewModel::openSettings,
            )
        } else {
            BibleStats(bible)
            BibleSectionStrip(current = section, onSelect = { sectionName = it.name })
            Box(Modifier.fillMaxWidth().weight(1f)) {
                key(section) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        when (section) {
                            BibleSection.OVERVIEW -> OverviewSection(bible)
                            BibleSection.WORLD -> WorldSection(bible)
                            BibleSection.POWER -> PowerSection(bible)
                            BibleSection.PEOPLE -> PeopleSection(bible)
                            BibleSection.FACTIONS -> FactionsSection(bible)
                            BibleSection.STORY -> StorySection(bible)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            BibleStartBar(
                hasBooks = document.adventures.isNotEmpty(),
                onStart = viewModel::startGameScreen,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 书封头部
// ---------------------------------------------------------------------------

@Composable
private fun BibleHero(
    bible: SettingBible,
    novelTitle: String,
    generating: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Secondary, Card.copy(alpha = 0.72f), Ink),
                ),
            ),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IF IT WERE YOU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (generating) "正在提取…" else "原著考据",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = novelTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Parchment,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (bible.author.isNotBlank()) {
                    Text(
                        text = "著 · ${bible.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                if (bible.status.isNotBlank()) {
                    HeroTag(
                        text = bible.status,
                        color = if (bible.status.contains("完结")) Forest else Amber,
                    )
                }
                if (bible.genre.isNotBlank()) {
                    HeroTag(text = bible.genre, color = AmberLight)
                }
            }
            if (bible.tone.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(34.dp)
                            .background(Amber.copy(alpha = 0.55f), RoundedCornerShape(1.dp)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "原著文风",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber.copy(alpha = 0.8f),
                        )
                        Text(
                            text = bible.tone,
                            style = MaterialTheme.typography.labelSmall,
                            color = Parchment.copy(alpha = 0.86f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (bible.synopsis.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = bible.synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun HeroTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.11f))
            .border(1.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun BibleStats(bible: SettingBible) {
    val stats = listOf(
        "人物" to bible.characters.size,
        "势力" to bible.factions.size,
        "地点" to bible.locations.size,
        "名场面" to bible.iconicScenes.size,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Border),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Parchment,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun BibleSectionStrip(
    current: BibleSection,
    onSelect: (BibleSection) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.92f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(BibleSection.entries, key = { it.name }) { section ->
            val selected = section == current
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(CircleShape)
                    .background(if (selected) Amber else Secondary.copy(alpha = 0.7f))
                    .border(
                        width = 1.dp,
                        color = if (selected) Amber else Border,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(section) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Ink else Muted,
                )
            }
        }
    }
    HorizontalDivider(color = Border)
}

// ---------------------------------------------------------------------------
// 总览：原著气质
// ---------------------------------------------------------------------------

@Composable
private fun OverviewSection(bible: SettingBible) {
    if (bible.synopsis.isNotBlank()) {
        SectionHeading("故事梗概")
        CardBox(backgroundColor = Card) {
            Text(
                text = bible.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment,
                lineHeight = 23.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.tone.isNotBlank() || bible.themes.isNotEmpty()) {
        SectionHeading("主题与文风")
        CardBox(
            backgroundColor = Secondary.copy(alpha = 0.5f),
            borderColor = Amber.copy(alpha = 0.20f),
        ) {
            Column {
                if (bible.themes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        bible.themes.forEach { theme -> Chip(text = theme, color = AmberLight) }
                    }
                }
                if (bible.tone.isNotBlank()) {
                    if (bible.themes.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    Text(
                        text = bible.tone,
                        style = MaterialTheme.typography.bodySmall,
                        color = Parchment.copy(alpha = 0.85f),
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.iconicScenes.isNotEmpty()) {
        SectionHeading("名场面", trailing = "${bible.iconicScenes.size} 个")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bible.iconicScenes) { scene -> SceneCard(scene) }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.quotes.isNotEmpty()) {
        SectionHeading("经典台词", trailing = "${bible.quotes.size} 句")
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            bible.quotes.forEach { quote -> QuoteCard(quote) }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.locations.isNotEmpty()) {
        SectionHeading("重要地点", trailing = "${bible.locations.size} 处")
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            bible.locations.forEachIndexed { index, location ->
                LocationCard(index + 1, location)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.keyItems.isNotEmpty()) {
        SectionHeading("关键物品", trailing = "${bible.keyItems.size} 件")
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            bible.keyItems.forEach { item -> ItemCard(item) }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.synopsis.isBlank() &&
        bible.tone.isBlank() &&
        bible.themes.isEmpty() &&
        bible.iconicScenes.isEmpty() &&
        bible.quotes.isEmpty() &&
        bible.locations.isEmpty() &&
        bible.keyItems.isEmpty()
    ) {
        EmptyHint("这份设定圣经还没有提取到文风、名场面与台词。")
    }
}

@Composable
private fun SceneCard(scene: IconicScene) {
    Column(
        modifier = Modifier
            .width(262.dp)
            .heightIn(min = 156.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Secondary, Card)))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(
            text = scene.title,
            style = MaterialTheme.typography.titleSmall,
            color = AmberLight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = scene.description,
            style = MaterialTheme.typography.bodySmall,
            color = Parchment.copy(alpha = 0.86f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 19.sp,
        )
        if (scene.impact.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = scene.impact,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuoteCard(quote: ClassicQuote) {
    CardBox(backgroundColor = Secondary.copy(alpha = 0.55f), borderColor = Border) {
        Row {
            Text(
                text = "“",
                style = MaterialTheme.typography.headlineMedium,
                color = Amber.copy(alpha = 0.55f),
            )
            Spacer(Modifier.width(5.dp))
            Column {
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Parchment,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp,
                )
                val meta = listOfNotNull(
                    quote.speaker.takeIf { it.isNotBlank() }?.let { "— $it" },
                    quote.context.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(index: Int, location: KeyLocation) {
    CardBox(backgroundColor = Card, padding = PaddingValues(13.dp)) {
        Row {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Secondary)
                    .border(1.dp, Amber.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Parchment,
                )
                if (location.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = location.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        lineHeight = 19.sp,
                    )
                }
                if (location.significance.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "意义 · ${location.significance}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Sage,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: KeyItem) {
    CardBox(backgroundColor = Card, padding = PaddingValues(13.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(Amber, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Parchment,
                )
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            if (item.significance.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "作用 · ${item.significance}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sage,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 世界观 / 力量体系 / 人物 / 势力 / 故事线
// ---------------------------------------------------------------------------

@Composable
private fun WorldSection(bible: SettingBible) {
    val fields = listOf(
        "世界总览" to bible.worldview.summary,
        "地理环境" to bible.worldview.geography,
        "历史背景" to bible.worldview.history,
        "运行规则" to bible.worldview.rules,
    ).filter { it.second.isNotBlank() }

    if (fields.isEmpty()) {
        EmptyHint("设定圣经里还没有世界观信息。")
        return
    }
    fields.forEachIndexed { index, (label, content) ->
        FieldCard(label, content)
        if (index != fields.lastIndex) Spacer(Modifier.height(11.dp))
    }
}

@Composable
private fun PowerSection(bible: SettingBible) {
    val power = bible.powerSystem
    if (power.name.isBlank() && power.description.isBlank() &&
        power.levels.isEmpty() && power.limitations.isBlank()
    ) {
        EmptyHint("这部小说没有明确的力量体系，或尚未提取到。")
        return
    }

    if (power.name.isNotBlank() || power.description.isNotBlank()) {
        CardBox(
            backgroundColor = Secondary.copy(alpha = 0.55f),
            borderColor = Amber.copy(alpha = 0.22f),
        ) {
            Column {
                if (power.name.isNotBlank()) {
                    Text(
                        text = power.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberLight,
                    )
                }
                if (power.description.isNotBlank()) {
                    if (power.name.isNotBlank()) Spacer(Modifier.height(6.dp))
                    Text(
                        text = power.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Parchment.copy(alpha = 0.86f),
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (power.levels.isNotEmpty()) {
        SectionHeading("等级阶梯", trailing = "${power.levels.size} 阶")
        CardBox(backgroundColor = Card) {
            Column {
                power.levels.forEachIndexed { index, level ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(27.dp)
                                    .clip(CircleShape)
                                    .background(Secondary)
                                    .border(1.dp, Amber.copy(alpha = 0.42f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Amber,
                                )
                            }
                            if (index != power.levels.lastIndex) {
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(Border),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = level,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Parchment,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (power.limitations.isNotBlank()) {
        SectionHeading("限制与代价")
        CardBox(
            backgroundColor = Crimson.copy(alpha = 0.07f),
            borderColor = Crimson.copy(alpha = 0.25f),
        ) {
            Text(
                text = power.limitations,
                style = MaterialTheme.typography.bodySmall,
                color = Parchment.copy(alpha = 0.88f),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun PeopleSection(bible: SettingBible) {
    if (bible.characters.isEmpty()) {
        EmptyHint("设定圣经里还没有人物，可以直接自创角色进入故事。")
        return
    }
    SectionHeading("出场人物", trailing = "${bible.characters.size} 位")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bible.characters.forEach { character -> CharacterCard(character) }
    }
}

@Composable
private fun CharacterCard(character: BibleCharacter) {
    CardBox(backgroundColor = Card, padding = PaddingValues(15.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Secondary, Card)),
                        )
                        .border(1.dp, Amber.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = character.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = Amber,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Parchment,
                    )
                    if (character.role.isNotBlank()) {
                        Text(
                            text = character.role,
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                            maxLines = 2,
                        )
                    }
                }
            }
            if (character.personality.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = character.personality,
                    style = MaterialTheme.typography.bodySmall,
                    color = Parchment.copy(alpha = 0.85f),
                    lineHeight = 19.sp,
                )
            }
            if (character.abilities.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "能力 · ${character.abilities}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sage,
                )
            }
            if (character.relationships.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "关系 · ${character.relationships}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun FactionsSection(bible: SettingBible) {
    if (bible.factions.isEmpty()) {
        EmptyHint("设定圣经里还没有势力阵营。")
        return
    }
    SectionHeading("势力阵营", trailing = "${bible.factions.size} 方")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bible.factions.forEach { faction -> FactionCard(faction) }
    }
}

@Composable
private fun FactionCard(faction: Faction) {
    CardBox(backgroundColor = Card, padding = PaddingValues(15.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = faction.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberLight,
                )
                Spacer(Modifier.weight(1f))
            }
            if (faction.goal.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "目标 · ${faction.goal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sage,
                )
            }
            if (faction.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = faction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    lineHeight = 19.sp,
                )
            }
            if (faction.keyMembers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    faction.keyMembers.forEach { member ->
                        Chip(text = member, color = Amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorySection(bible: SettingBible) {
    if (bible.timeline.isEmpty() && bible.mysteries.isEmpty()) {
        EmptyHint("这部小说的故事线与未解之谜还没有提取出来。")
        return
    }

    if (bible.timeline.isNotEmpty()) {
        SectionHeading("时间线", trailing = "${bible.timeline.size} 个事件")
        CardBox(backgroundColor = Card) {
            Column {
                bible.timeline.forEachIndexed { index, event ->
                    TimelineRow(event, isLast = index == bible.timeline.lastIndex)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (bible.mysteries.isNotEmpty()) {
        SectionHeading("未解之谜", trailing = "${bible.mysteries.size} 条")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bible.mysteries.forEach { mystery -> MysteryCard(mystery) }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(Amber, CircleShape),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(46.dp)
                        .background(Border),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Text(
                text = event.event,
                style = MaterialTheme.typography.titleSmall,
                color = Parchment,
            )
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun MysteryCard(mystery: Mystery) {
    CardBox(
        backgroundColor = Crimson.copy(alpha = 0.05f),
        borderColor = Crimson.copy(alpha = 0.20f),
        padding = PaddingValues(14.dp),
    ) {
        Column {
            Text(
                text = mystery.question,
                style = MaterialTheme.typography.titleSmall,
                color = Parchment,
            )
            if (mystery.clues.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "线索 · ${mystery.clues}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sage,
                )
            }
            if (mystery.theories.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "猜测 · ${mystery.theories}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 共用组件
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(Amber, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Parchment,
        )
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun FieldCard(label: String, content: String) {
    CardBox(backgroundColor = Card) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment.copy(alpha = 0.90f),
                lineHeight = 23.sp,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    CardBox(
        backgroundColor = Card.copy(alpha = 0.40f),
        borderColor = Border.copy(alpha = 0.65f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BibleStartBar(hasBooks: Boolean, onStart: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Ink,
        shadowElevation = 16.dp,
    ) {
        Column {
            HorizontalDivider(color = Border)
            Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                EmberButton(
                    text = if (hasBooks) "打开故事书" else "选择角色，开始冒险",
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStart,
                )
            }
        }
    }
}

@Composable
private fun BibleGenerationContent(
    document: ProjectDocument,
    state: BibleUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        if (state.isGenerating) {
            LoadingBlock(
                title = "正在考据这部小说…",
                subtitle = "提取世界观、人物关系、名场面与经典台词，尽量保留原著气质",
                rawText = state.rawText,
            )
        } else if (state.error.isNotBlank()) {
            InlineError(state.error, onOpenSettings = onOpenSettings)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "重试生成",
                    icon = Icons.Outlined.Refresh,
                    onClick = onRetry,
                )
                GhostButton(text = "前往 AI 设置", onClick = onOpenSettings)
            }
        } else {
            LoadingBlock(
                title = "准备生成《${document.novelName}》的设定圣经…",
                subtitle = "稍等片刻，正在整理检索资料",
            )
        }
    }
}
