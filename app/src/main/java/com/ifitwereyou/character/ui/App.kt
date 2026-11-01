package com.ifitwereyou.character.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifitwereyou.character.ui.components.AppBackdrop
import com.ifitwereyou.character.ui.screens.BibleScreen
import com.ifitwereyou.character.ui.screens.CheckScreen
import com.ifitwereyou.character.ui.screens.GameScreen
import com.ifitwereyou.character.ui.screens.HomeScreen
import com.ifitwereyou.character.ui.screens.SettingsScreen
import com.ifitwereyou.character.ui.theme.Amber
import com.ifitwereyou.character.ui.theme.Border
import com.ifitwereyou.character.ui.theme.Card
import com.ifitwereyou.character.ui.theme.Muted
import com.ifitwereyou.character.ui.theme.Parchment

@Composable
fun CharacterApp(viewModel: AppViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportProject(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importProject(uri)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        viewModel.handleBack()
    }

    AppBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onImport = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                )

                Screen.BIBLE -> BibleScreen(
                    viewModel = viewModel,
                    onExport = { exportLauncher.launch(viewModel.suggestedExportName()) },
                )

                Screen.CHECK -> CheckScreen(
                    viewModel = viewModel,
                    onExport = { exportLauncher.launch(viewModel.suggestedExportName()) },
                )

                Screen.GAME -> GameScreen(
                    viewModel = viewModel,
                    onExport = { exportLauncher.launch(viewModel.suggestedExportName()) },
                )

                Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }

            if (screen != Screen.SETTINGS) {
                SettingsFab(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 12.dp),
                    onClick = viewModel::openSettings,
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) { data ->
                Snackbar(
                    containerColor = Card,
                    contentColor = Parchment,
                    shape = RoundedCornerShape(6.dp),
                    actionContentColor = Amber,
                ) {
                    Text(data.visuals.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsFab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = Card.copy(alpha = 0.94f),
        contentColor = Muted,
        border = BorderStroke(1.dp, Border),
        shadowElevation = 6.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "AI 设置",
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
