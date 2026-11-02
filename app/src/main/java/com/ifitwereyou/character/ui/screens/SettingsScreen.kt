@file:OptIn(ExperimentalLayoutApi::class)

package com.ifitwereyou.character.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.ifitwereyou.character.BuildConfig
import com.ifitwereyou.character.ai.AI_PRESETS
import com.ifitwereyou.character.ai.AiPreset
import com.ifitwereyou.character.ai.isInsecurePublicBaseUrl
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.SettingsBusy
import com.ifitwereyou.character.ui.components.AppSpinner
import com.ifitwereyou.character.ui.components.CardBox
import com.ifitwereyou.character.ui.components.Chip
import com.ifitwereyou.character.ui.components.EmberButton
import com.ifitwereyou.character.ui.components.GhostButton
import com.ifitwereyou.character.ui.components.NoticeBanner
import com.ifitwereyou.character.ui.components.ScreenScaffold
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
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val busy = state.busy != null

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Card,
            titleContentColor = Parchment,
            textContentColor = Muted,
            title = { Text("清除 AI 配置？") },
            text = { Text("Base URL、API Key 与模型名都会从本机删除，之后需要重新填写。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearSettings()
                    },
                ) {
                    Text("清除", color = Crimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = Muted)
                }
            },
        )
    }

    ScreenScaffold(
        title = "AI 设置",
        subtitle = "配置只保存在本机，不会上传",
        onBack = viewModel::closeSettings,
        bottomBar = {
            Column {
                state.notice?.let { notice ->
                    NoticeBanner(notice)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = if (state.busy == SettingsBusy.TEST) "测试中…" else "测试连接",
                        icon = Icons.Outlined.CheckCircle,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::testConnection,
                    )
                    EmberButton(
                        text = if (busy) "请稍候…" else "保存设置",
                        icon = Icons.Outlined.Check,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::saveSettings,
                    )
                }
            }
        },
    ) {
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

        Spacer(Modifier.height(14.dp))
        CardBox(backgroundColor = Card) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "模型参数（可选）",
                            style = MaterialTheme.typography.titleMedium,
                            color = Parchment,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "留空表示使用各场景默认值；采样参数统一覆盖创作类请求，最大输出 Token 填写后覆盖场景上限（推理模型可调高）。服务商不支持时会自动降级重试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                    TextButton(onClick = viewModel::resetModelParams) {
                        Text("恢复默认", style = MaterialTheme.typography.labelSmall, color = Amber)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamField(
                        label = "Temperature",
                        value = state.params.temperature,
                        placeholder = "空 = 场景默认",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = viewModel::setTemperature,
                        modifier = Modifier.weight(1f),
                    )
                    ParamField(
                        label = "Top P",
                        value = state.params.topP,
                        placeholder = "空 = 场景默认",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = viewModel::setTopP,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamField(
                        label = "最大输出 Token",
                        value = state.params.maxTokens,
                        placeholder = "空 = 场景默认",
                        keyboardType = KeyboardType.Number,
                        onValueChange = viewModel::setMaxTokens,
                        modifier = Modifier.weight(1f),
                    )
                    ParamField(
                        label = "Seed",
                        value = state.params.seed,
                        placeholder = "空 = 不固定",
                        keyboardType = KeyboardType.Number,
                        onValueChange = viewModel::setSeed,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamField(
                        label = "Frequency Penalty",
                        value = state.params.frequencyPenalty,
                        placeholder = "-2.0 ~ 2.0",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = viewModel::setFrequencyPenalty,
                        modifier = Modifier.weight(1f),
                    )
                    ParamField(
                        label = "Presence Penalty",
                        value = state.params.presencePenalty,
                        placeholder = "-2.0 ~ 2.0",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = viewModel::setPresencePenalty,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = state.params.extraParams,
                    onValueChange = viewModel::setExtraParams,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("自定义请求参数（JSON，可选）") },
                    placeholder = { Text("{\"reasoning_effort\":\"low\"}") },
                    supportingText = {
                        Text(
                            "会合并进请求体，适合 response_format、reasoning_effort、stop 等服务商专有参数；不能覆盖 model / messages / stream。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )

                if (isInsecurePublicBaseUrl(state.form.baseUrl)) {
                    Text(
                        "⚠ 当前 Base URL 是公网明文 HTTP，API Key 会以明文经过网络。建议改用 https://，或确认这是可信局域网。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Crimson,
                    )
                }
            }
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
                text = "清除配置",
                enabled = !busy,
                onClick = { showClearConfirm = true },
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

        Spacer(Modifier.height(18.dp))
        Text(
            text = "假如是你 v${BuildConfig.VERSION_NAME} · 单机版",
            style = MaterialTheme.typography.labelSmall,
            color = Muted.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // 提示条会临时撑高吸底操作栏，留出余量避免最后一段内容被遮住。
        Spacer(Modifier.height(64.dp))
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
private fun ParamField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = appTextFieldColors(),
        shape = MaterialTheme.shapes.medium,
    )
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
