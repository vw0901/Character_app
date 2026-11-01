@file:OptIn(ExperimentalLayoutApi::class)

package com.ifitwereyou.character.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.BibleCharacter
import com.ifitwereyou.character.data.Faction
import com.ifitwereyou.character.data.Mystery
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.LoadingBlock
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.ScreenScaffold
import com.ifitwereyou.character.ui.components.SectionCard
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.AmberLight
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary
import com.ifitwereyou.character.ui.theme.Sage

@OptIn(ExperimentalLayoutApi::class)
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
    ScreenScaffold(
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
                    MenuAction(
                        label = "查看体检报告",
                        enabled = document.check != null,
                        onClick = viewModel::openCheck,
                    ),
                    MenuAction("AI 设置", onClick = viewModel::openSettings),
                    MenuAction("返回书架", onClick = viewModel::goHome),
                ),
            )
        },
        bottomBar = if (bible != null) {
            {
                val checked = document.check != null
                EmberButton(
                    text = if (checked) "查看漏洞体检报告" else "开始漏洞体检",
                    icon = Icons.Outlined.Warning,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (checked) viewModel::openCheck else viewModel::startCheckScreen,
                )
            }
        } else {
            null
        },
    ) {
        BibleMeta(document, bible)
        if (bible != null && bible.synopsis.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Row {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(58.dp)
                        .background(Amber.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    bible.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )
            }
        }
        val repairCount = document.repair?.appliedFixes?.size ?: 0
        if (repairCount > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .background(Forest.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
                    .border(1.dp, Forest.copy(alpha = 0.30f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Forest, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "已按体检报告修补 $repairCount 处设定",
                    style = MaterialTheme.typography.bodySmall,
                    color = Forest,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        when {
            state.isGenerating && bible == null -> {
                LoadingBlock(
                    title = "正在解析小说设定…",
                    subtitle = "从搜索结果中提取世界观、人物、力量体系等结构化信息",
                    rawText = state.rawText,
                )
            }

            bible == null && state.error.isNotBlank() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InlineError(state.error, onOpenSettings = viewModel::openSettings)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = "重试生成",
                            icon = Icons.Outlined.Refresh,
                            onClick = viewModel::retryBible,
                        )
                        GhostButton(
                            text = "前往 AI 设置",
                            onClick = viewModel::openSettings,
                        )
                    }
                }
            }

            bible != null -> {
                BibleSections(
                    bible = bible,
                    expanded = state.expandedSections,
                    onToggle = viewModel::toggleBibleSection,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BibleMeta(document: ProjectDocument, bible: SettingBible?) {
    val novelName = document.search?.novelName?.ifBlank { document.novelName } ?: document.novelName
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(text = novelName, color = Amber)
        if (bible != null && bible.author.isNotBlank()) {
            Text(bible.author, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (bible != null && bible.status.isNotBlank()) {
            val statusColor = if (bible.status.contains("完结")) Forest else Amber
            Chip(text = bible.status, color = statusColor)
        }
        if (bible != null && bible.genre.isNotBlank()) {
            Text(bible.genre, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun BibleSections(
    bible: SettingBible,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(
            title = "世界观",
            icon = Icons.Outlined.Public,
            expanded = expanded.contains("worldview"),
            onToggle = { onToggle("worldview") },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBlock("世界概述", bible.worldview.summary)
                InfoBlock("地理环境", bible.worldview.geography)
                InfoBlock("历史背景", bible.worldview.history)
                InfoBlock("运行规则", bible.worldview.rules)
            }
        }

        SectionCard(
            title = "力量体系",
            icon = Icons.Outlined.Bolt,
            expanded = expanded.contains("powerSystem"),
            onToggle = { onToggle("powerSystem") },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBlock("体系名称", bible.powerSystem.name)
                InfoBlock("体系说明", bible.powerSystem.description)
                InfoBlock("限制条件", bible.powerSystem.limitations)
                if (bible.powerSystem.levels.isNotEmpty()) {
                    Column {
                        Text("等级体系", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Spacer(Modifier.height(7.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            bible.powerSystem.levels.forEachIndexed { index, level ->
                                Chip(text = "${index + 1}. $level", color = AmberLight)
                            }
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "势力阵营",
            icon = Icons.Outlined.Groups,
            expanded = expanded.contains("factions"),
            onToggle = { onToggle("factions") },
        ) {
            if (bible.factions.isEmpty()) {
                Text("暂未提取到势力。", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bible.factions.forEach { faction -> FactionCard(faction) }
                }
            }
        }

        SectionCard(
            title = "核心人物",
            icon = Icons.Outlined.Person,
            expanded = expanded.contains("characters"),
            onToggle = { onToggle("characters") },
        ) {
            if (bible.characters.isEmpty()) {
                Text("暂未提取到人物，可直接自创角色进入故事。", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bible.characters.forEach { character -> CharacterCard(character) }
                }
            }
        }

        SectionCard(
            title = "时间线",
            icon = Icons.Outlined.DateRange,
            expanded = expanded.contains("timeline"),
            onToggle = { onToggle("timeline") },
        ) {
            if (bible.timeline.isEmpty()) {
                Text("暂未提取到时间线。", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    bible.timeline.forEach { event ->
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 5.dp)
                                    .size(8.dp)
                                    .background(Amber, CircleShape),
                            )
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(event.event, style = MaterialTheme.typography.titleSmall, color = Parchment)
                                if (event.description.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(event.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "未解之谜",
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            expanded = expanded.contains("mysteries"),
            onToggle = { onToggle("mysteries") },
        ) {
            if (bible.mysteries.isEmpty()) {
                Text("暂未提取到未解之谜。", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bible.mysteries.forEach { mystery -> MysteryCard(mystery) }
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Parchment)
    }
}

@Composable
private fun FactionCard(faction: Faction) {
    CardBox(backgroundColor = Secondary.copy(alpha = 0.55f), padding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Column {
            Text(faction.name, style = MaterialTheme.typography.titleSmall, color = Amber)
            if (faction.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(faction.description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (faction.goal.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("目标：${faction.goal}", style = MaterialTheme.typography.bodySmall, color = Sage)
            }
            if (faction.keyMembers.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "核心成员：${faction.keyMembers.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(character: BibleCharacter) {
    CardBox(backgroundColor = Secondary.copy(alpha = 0.55f), padding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(character.name, style = MaterialTheme.typography.titleSmall, color = Amber)
                if (character.role.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Chip(text = character.role, color = AmberLight)
                }
            }
            if (character.personality.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(character.personality, style = MaterialTheme.typography.bodySmall, color = Parchment)
            }
            if (character.abilities.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text("能力：${character.abilities}", style = MaterialTheme.typography.bodySmall, color = Sage)
            }
            if (character.relationships.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("关系：${character.relationships}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun MysteryCard(mystery: Mystery) {
    CardBox(backgroundColor = Secondary.copy(alpha = 0.55f), padding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = Crimson, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    mystery.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = Parchment,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (mystery.clues.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("线索：${mystery.clues}", style = MaterialTheme.typography.bodySmall, color = Sage)
            }
            if (mystery.theories.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("猜测：${mystery.theories}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}
