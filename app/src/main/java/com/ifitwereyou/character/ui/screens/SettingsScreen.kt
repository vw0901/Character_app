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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.ai.AI_PRESETS
import com.ifitwereyou.character.ai.AiPreset
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.SettingsBusy
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.NoticeBanner
import com.ifitwereyou.character.ui.components.ScreenHeader
import com.ifitwereyou.character.ui.components.appTextFieldColors
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.AmberLight
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Crimson
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment
import com.ifitwereyou.character.ui.theme.Secondary

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val busy = state.busy != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::closeSettings) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Muted)
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                ScreenHeader(
                    title = "AI 设置",
                    icon = Icons.Outlined.Settings,
                    subtitle = "配置 OpenAI 兼容服务，设置只保存在本机",
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        CardBox(backgroundColor = Card) {
            Column {
                Text("服务商预设", style = MaterialTheme.typography.titleMedium, color = Parchment)
                Spacer(Modifier.height(2.dp))
                Text(
                    "选择预设后仍可手动修改下面的连接参数",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AI_PRESETS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                PresetCard(
                                    preset = preset,
                                    selected = state.selectedPresetId == preset.id,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.selectPreset(preset.id) },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        CardBox(backgroundColor = Card) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = state.form.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL *") },
                    placeholder = { Text("例如：http://127.0.0.1:11434/v1") },
                    supportingText = {
                        Text(
                            "需指向 OpenAI 兼容接口的版本根路径；缺少 /v1 时会自动补全。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = state.form.apiKey,
                    onValueChange = viewModel::setApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    placeholder = { Text("本地模型可留空") },
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(
                                if (showApiKey) "隐藏" else "显示",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                            )
                        }
                    },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = state.form.model,
                    onValueChange = viewModel::setModel,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("模型名称 *") },
                    placeholder = { Text("例如：qwen2.5:14b") },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )
                if (state.models.isNotEmpty()) {
                    Column {
                        Text(
                            "已获取的模型（点选填入）",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.models.forEach { model ->
                                Chip(
                                    text = model,
                                    color = if (state.form.model == model) Amber else AmberLight,
                                    onClick = { viewModel.setModel(model) },
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("联网检索", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderButton(
                            text = "不启用",
                            selected = state.form.searchProvider != "tavily",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setSearchProvider("none") },
                        )
                        ProviderButton(
                            text = "Tavily",
                            selected = state.form.searchProvider == "tavily",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setSearchProvider("tavily") },
                        )
                    }
                    Text(
                        "不启用时由模型知识兜底。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.form.searchProvider == "tavily") {
                    OutlinedTextField(
                        value = state.form.tavilyApiKey,
                        onValueChange = viewModel::setTavilyKey,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tavily API Key") },
                        placeholder = { Text("tvly-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = appTextFieldColors(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }

        state.notice?.let { notice ->
            Spacer(Modifier.height(14.dp))
            NoticeBanner(notice)
        }

        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            GhostButton(
                text = if (state.busy == SettingsBusy.MODELS) "获取中…" else "获取模型",
                icon = Icons.Outlined.Refresh,
                enabled = !busy,
                onClick = viewModel::fetchModels,
            )
            GhostButton(
                text = if (state.busy == SettingsBusy.TEST) "测试中…" else "测试连接",
                icon = Icons.Outlined.CheckCircle,
                enabled = !busy,
                onClick = viewModel::testConnection,
            )
            EmberButton(
                text = if (state.busy == SettingsBusy.TEST) "请稍候…" else "保存设置",
                icon = Icons.Outlined.Check,
                enabled = !busy,
                onClick = viewModel::saveSettings,
            )
            GhostButton(
                text = "清除配置",
                enabled = !busy,
                onClick = viewModel::clearSettings,
            )
        }
        if (state.busy != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSpinner(size = 15)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.busy == SettingsBusy.MODELS) "正在向服务商请求模型列表…" else "正在测试连接…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Secondary.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                .border(1.dp, Border, MaterialTheme.shapes.medium)
                .padding(13.dp),
        ) {
            Text(
                "配置只保存在本机的 SharedPreferences 中，不会写入项目存档；所有 AI 请求都从手机直接发往你填写的服务商。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "共用设备请在离开前点「清除配置」。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: AiPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CardBox(
        modifier = modifier.heightIn(min = 66.dp),
        backgroundColor = if (selected) Amber.copy(alpha = 0.12f) else Secondary.copy(alpha = 0.4f),
        borderColor = if (selected) Amber.copy(alpha = 0.65f) else Border,
        onClick = onClick,
        padding = PaddingValues(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                preset.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) AmberLight else Parchment,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                preset.description,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ProviderButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CardBox(
        modifier = modifier,
        backgroundColor = if (selected) Amber else Card,
        borderColor = if (selected) Amber else Border,
        onClick = onClick,
        padding = PaddingValues(vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) com.ifitwereyou.character.ui.theme.Ink else Muted,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
