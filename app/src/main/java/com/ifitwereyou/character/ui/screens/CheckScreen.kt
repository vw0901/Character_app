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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.CheckIssue
import com.ifitwereyou.character.data.CheckResult
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.LoadingBlock
import com.ifitwereyou.character.ui.components.MenuAction
import com.ifitwereyou.character.ui.components.OverflowMenu
import com.ifitwereyou.character.ui.components.ScoreRing
import com.ifitwereyou.character.ui.components.ScreenScaffold
import com.ifitwereyou.character.ui.components.TopBarIcon
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Forest
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Sage

@Composable
fun CheckScreen(
    viewModel: AppViewModel,
    onExport: () -> Unit,
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val state by viewModel.checkState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    val document = project
    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在打开体检报告…", color = Muted)
        }
        return
    }

    val result = document.check
    val novelTitle = document.bible?.novelName?.ifBlank { document.novelName } ?: document.novelName
    ScreenScaffold(
        title = "漏洞体检",
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
                    MenuAction("查看设定圣经", onClick = viewModel::openBible),
                    MenuAction("AI 设置", onClick = viewModel::openSettings),
                ),
            )
        },
        bottomBar = if (result != null) {
            {
                EmberButton(
                    text = if (document.repair != null) "以修补后的设定开始冒险" else "开始文字冒险",
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::startGameScreen,
                )
            }
        } else {
            null
        },
    ) {
        when {
            state.isChecking && result == null -> {
                LoadingBlock(
                    title = "正在进行漏洞体检…",
                    subtitle = "审查世界观一致性、力量平衡、人物逻辑、时间线合理性",
                    rawText = state.rawText,
                )
            }

            result == null && state.error.isNotBlank() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InlineError(state.error, onOpenSettings = viewModel::openSettings)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = "重试体检",
                            icon = Icons.Outlined.Refresh,
                            onClick = viewModel::retryCheck,
                        )
                        GhostButton(text = "前往 AI 设置", onClick = viewModel::openSettings)
                    }
                }
            }

            result != null -> {
                CheckResultContent(
                    document = document,
                    result = result,
                    isRepairing = state.isRepairing,
                    repairRaw = state.repairRaw,
                    repairError = state.repairError,
                    skipRepair = state.skipRepair,
                    onRetryRepair = viewModel::retryRepair,
                    onSkipRepair = viewModel::skipRepair,
                )
            }
        }
    }
}

@Composable
private fun CheckResultContent(
    document: ProjectDocument,
    result: CheckResult,
    isRepairing: Boolean,
    repairRaw: String,
    repairError: String,
    skipRepair: Boolean,
    onRetryRepair: () -> Unit,
    onSkipRepair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CardBox(
            backgroundColor = Card,
            borderColor = Amber.copy(alpha = 0.30f),
            padding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设定健康度", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                result.overallScore.toString(),
                                style = MaterialTheme.typography.displayMedium,
                                color = scoreColor(result.overallScore),
                            )
                            Text(
                                " / 100",
                                style = MaterialTheme.typography.titleMedium,
                                color = Muted,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                    ScoreRing(score = result.overallScore)
                }
                if (result.summary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Parchment,
                    )
                }
            }
        }

        if (result.strengths.isNotEmpty()) {
            CardBox(backgroundColor = Card) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Star, contentDescription = null, tint = Forest, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("设定亮点", style = MaterialTheme.typography.titleMedium, color = Forest)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        result.strengths.forEach { strength ->
                            Chip(text = strength, color = Forest)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Crimson, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "发现的漏洞（${result.issues.size}）",
                style = MaterialTheme.typography.titleMedium,
                color = Crimson,
            )
        }
        result.issues.forEach { issue -> IssueCard(issue) }

        CardBox(backgroundColor = Card) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Build, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("设定修补", style = MaterialTheme.typography.titleMedium, color = Parchment)
                }
                Spacer(Modifier.height(12.dp))

                val repair = document.repair
                when {
                    isRepairing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.ifitwereyou.character.ui.components.AppSpinner(size = 16)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在按修补方案补全设定，后续冒险会使用修补后的版本…",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                        if (repairRaw.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                repairRaw,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                color = Muted,
                                maxLines = 10,
                            )
                        }
                    }

                    repair != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Forest, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "已修补 ${repair.appliedFixes.size} 处，冒险将使用补全后的设定",
                                style = MaterialTheme.typography.bodySmall,
                                color = Forest,
                            )
                        }
                        if (repair.summary.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(repair.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        repair.appliedFixes.forEach { fix ->
                            Spacer(Modifier.height(8.dp))
                            CardBox(
                                backgroundColor = Forest.copy(alpha = 0.06f),
                                borderColor = Forest.copy(alpha = 0.22f),
                                padding = androidx.compose.foundation.layout.PaddingValues(11.dp),
                            ) {
                                Column {
                                    Text(fix.issue, style = MaterialTheme.typography.bodySmall, color = Parchment)
                                    if (fix.action.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(fix.action, style = MaterialTheme.typography.labelSmall, color = Muted)
                                    }
                                }
                            }
                        }
                    }

                    repairError.isNotBlank() -> {
                        Text(repairError, style = MaterialTheme.typography.bodySmall, color = Crimson)
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(text = "重试修补", onClick = onRetryRepair)
                            GhostButton(text = "跳过修补", onClick = onSkipRepair)
                        }
                    }

                    skipRepair -> {
                        Text(
                            "已跳过修补，将直接使用原始设定开始冒险。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }

                    else -> {
                        Text(
                            "体检完成后会自动按修补方案补全设定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        if (document.repair == null && !isRepairing && repairError.isBlank()) {
            Spacer(Modifier.height(2.dp))
            GhostButton(
                text = "跳过修补，直接用原始设定",
                modifier = Modifier.fillMaxWidth(),
                onClick = onSkipRepair,
            )
        }
    }
}

@Composable
private fun IssueCard(issue: CheckIssue) {
    CardBox(backgroundColor = Card) {
        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Chip(text = issue.severity.ifBlank { "未分级" }, color = severityColor(issue.severity), filled = true)
                if (issue.category.isNotBlank()) {
                    Chip(text = issue.category, color = Muted)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(issue.title, style = MaterialTheme.typography.titleSmall, color = Parchment)
            if (issue.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(issue.description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (issue.fix.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Forest.copy(alpha = 0.06f), MaterialTheme.shapes.small)
                        .border(1.dp, Forest.copy(alpha = 0.20f), MaterialTheme.shapes.small)
                        .padding(11.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Build, contentDescription = null, tint = Forest, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text("修补方案", style = MaterialTheme.typography.labelSmall, color = Forest)
                        Spacer(Modifier.height(2.dp))
                        Text(issue.fix, style = MaterialTheme.typography.bodySmall, color = Parchment)
                    }
                }
            }
        }
    }
}

private fun severityColor(severity: String) = when (severity) {
    "高" -> Crimson
    "中" -> Amber
    "低" -> Sage
    else -> Muted
}

private fun scoreColor(score: Int) = when {
    score >= 80 -> Forest
    score >= 60 -> Amber
    else -> Crimson
}
