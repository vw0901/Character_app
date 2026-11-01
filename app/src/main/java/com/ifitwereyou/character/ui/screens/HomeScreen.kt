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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.data.ProjectSummary
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.EmptyHint
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.InlineError
import com.ifitwereyou.character.ui.components.appTextFieldColors
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary

private val SUGGESTIONS = listOf("斗罗大陆", "三体", "射雕英雄传", "魏临美小说", "哈利波特")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onImport: () -> Unit,
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val projects by viewModel.savedProjects.collectAsStateWithLifecycle()
    var novelName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 书与灯：首页的视觉锚点
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Card, Secondary)),
                    shape = RoundedCornerShape(16.dp),
                )
                .border(1.dp, Border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(22.dp))

        Text(
            text = "假如是你",
            style = MaterialTheme.typography.displaySmall,
            color = Parchment,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "输入一部小说，生成它的设定圣经，\n再以「你」为主角走进故事",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = novelName,
                onValueChange = {
                    novelName = it
                    if (homeState.error.isNotEmpty()) viewModel.clearHomeMessage()
                },
                modifier = Modifier.weight(1f),
                enabled = !homeState.isSearching,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                placeholder = {
                    Text(
                        "输入小说名，如《三体》…",
                        color = Muted.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (novelName.isNotBlank() && !homeState.isSearching) {
                        viewModel.searchNovel(novelName)
                    }
                }),
            )
            Spacer(Modifier.width(10.dp))
            EmberButton(
                text = if (homeState.isSearching) "检索中" else "检索",
                enabled = !homeState.isSearching && novelName.isNotBlank(),
                modifier = Modifier.height(56.dp),
                onClick = { viewModel.searchNovel(novelName) },
            )
        }

        if (homeState.isSearching) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSpinner(size = 16)
                Spacer(Modifier.width(8.dp))
                Text("正在整理这部小说的资料…", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        if (homeState.error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            InlineError(
                message = homeState.error,
                onOpenSettings = viewModel::openSettings,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(
                text = "导入 JSON 存档",
                icon = Icons.Outlined.Upload,
                onClick = onImport,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "支持项目存档 / 设定圣经 / 检索结果",
                style = MaterialTheme.typography.labelSmall,
                color = Muted.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.height(24.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUGGESTIONS.forEach { name ->
                Chip(
                    text = name,
                    color = if (novelName == name) Amber else Muted,
                    onClick = { novelName = name },
                )
            }
        }

        Spacer(Modifier.height(34.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("本机存档", style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Spacer(Modifier.height(10.dp))

        if (projects.isEmpty()) {
            EmptyHint("还没有存档。检索一部小说后会自动保存为 JSON 文件。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.take(6).forEach { item ->
                    ProjectRow(
                        item = item,
                        loading = homeState.loadingProjectId == item.id,
                        onClick = { viewModel.loadSavedProject(item.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "AI 配置只保存在本机，不会上传；项目存档同样只写在本机。",
            style = MaterialTheme.typography.labelSmall,
            color = Muted.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProjectRow(
    item: ProjectSummary,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val status = when {
        item.hasAdventure -> "冒险进行中"
        item.hasRepair -> "已修补设定"
        item.hasBible -> "设定圣经"
        else -> "检索结果"
    }
    CardBox(
        onClick = if (loading) null else onClick,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        backgroundColor = Card.copy(alpha = 0.65f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                item.novelName,
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment,
                modifier = Modifier.weight(1f),
            )
            Text(status, style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.width(10.dp))
            if (loading) {
                AppSpinner(size = 15)
            } else {
                Text("›", style = MaterialTheme.typography.titleMedium, color = Muted)
            }
        }
    }
}
