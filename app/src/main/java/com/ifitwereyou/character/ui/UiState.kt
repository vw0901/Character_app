package com.ifitwereyou.character.ui

import com.ifitwereyou.character.ai.AiConfig
import com.ifitwereyou.character.ai.DEFAULT_PRESET_ID
import com.ifitwereyou.character.ai.initialSettingsForm
import com.ifitwereyou.character.data.CharacterChoice

enum class Screen { HOME, BIBLE, STORIES, GAME, EPILOGUE, SETTINGS }

enum class NoticeKind { SUCCESS, ERROR, INFO }

data class Notice(val kind: NoticeKind, val text: String)

data class HomeUiState(
    val isSearching: Boolean = false,
    val error: String = "",
    val loadingProjectId: String = "",
)

data class BibleUiState(
    val isGenerating: Boolean = false,
    val rawText: String = "",
    val error: String = "",
    val attempt: Int = 0,
)

data class GameUiState(
    val pickerCharacter: CharacterChoice? = null,
    val isGeneratingStories: Boolean = false,
    val storyError: String = "",
    val isInitializing: Boolean = false,
    val isProcessing: Boolean = false,
    val isVerifyingGoal: Boolean = false,
    val isWritingEnding: Boolean = false,
    val endingError: String = "",
    val endingDraft: String = "",
    val isWritingEpilogue: Boolean = false,
    val epilogueError: String = "",
    val epilogueDraft: String = "",
    val narration: String = "",
    val displayedText: String = "",
    val isTyping: Boolean = false,
    val error: String = "",
    val customText: String = "",
    val originalCustomText: String = "",
    val isPolishing: Boolean = false,
    val polishError: String = "",
) {
    val busy: Boolean
        get() = isInitializing || isProcessing
}

enum class SettingsBusy { MODELS, TEST }

data class SettingsUiState(
    val form: AiConfig = initialSettingsForm(),
    val selectedPresetId: String = DEFAULT_PRESET_ID,
    val models: List<String> = emptyList(),
    val busy: SettingsBusy? = null,
    val notice: Notice? = null,
)
