package com.ifitwereyou.character.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ifitwereyou.character.ai.AI_PRESETS
import com.ifitwereyou.character.ai.AiClient
import com.ifitwereyou.character.ai.AiConfig
import com.ifitwereyou.character.ai.AiConfigurationException
import com.ifitwereyou.character.ai.AiServiceException
import com.ifitwereyou.character.ai.DEFAULT_PRESET_ID
import com.ifitwereyou.character.ai.NOT_CONFIGURED_MESSAGE
import com.ifitwereyou.character.ai.Prompts
import com.ifitwereyou.character.ai.SearchService
import com.ifitwereyou.character.ai.SettingsStore
import com.ifitwereyou.character.ai.emptyAiConfig
import com.ifitwereyou.character.ai.getAiErrorMessage
import com.ifitwereyou.character.ai.initialSettingsForm
import com.ifitwereyou.character.ai.normalizeBaseUrl
import com.ifitwereyou.character.ai.validateAiParams
import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.EndingType
import com.ifitwereyou.character.data.HistoryEntry
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.ImportKind
import com.ifitwereyou.character.data.MAX_UNDO_SNAPSHOTS
import com.ifitwereyou.character.data.ProjectCodec
import com.ifitwereyou.character.data.ProjectDocument
import com.ifitwereyou.character.data.ProjectPhase
import com.ifitwereyou.character.data.ProjectStore
import com.ifitwereyou.character.data.ProjectSummary
import com.ifitwereyou.character.data.SaveState
import com.ifitwereyou.character.data.SearchData
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.data.StoryOption
import com.ifitwereyou.character.data.phaseForProject
import com.ifitwereyou.character.data.createProjectFromSearch
import com.ifitwereyou.character.data.normalizeBible
import com.ifitwereyou.character.data.normalizeGameOpening
import com.ifitwereyou.character.data.normalizeGoalCheck
import com.ifitwereyou.character.data.normalizeSceneResult
import com.ifitwereyou.character.data.normalizeStoryOptions
import com.ifitwereyou.character.data.nowIso
import com.ifitwereyou.character.data.parseImportedJson
import com.ifitwereyou.character.data.parseJsonLoose
import com.ifitwereyou.character.data.projectFileName
import com.ifitwereyou.character.game.characterKey
import com.ifitwereyou.character.game.createAdventure
import com.ifitwereyou.character.game.detectGoalAchievedFromText
import com.ifitwereyou.character.game.joinNarration
import com.ifitwereyou.character.game.resolveOutcome
import com.ifitwereyou.character.game.snapshotOf
import com.ifitwereyou.character.game.storyKey
import com.ifitwereyou.character.game.applyStatChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.StandardCharsets

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val projectStore = ProjectStore.forContext(application)
    private val settingsStore = SettingsStore(application)
    private val aiClient = AiClient()
    private val searchService = SearchService(aiClient)

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen = _screen.asStateFlow()

    private val _project = MutableStateFlow<ProjectDocument?>(null)
    val project = _project.asStateFlow()

    private val _savedProjects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val savedProjects = _savedProjects.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState = _saveState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState = _homeState.asStateFlow()

    private val _bibleState = MutableStateFlow(BibleUiState())
    val bibleState = _bibleState.asStateFlow()

    private val _gameState = MutableStateFlow(GameUiState())
    val gameState = _gameState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState = _settingsState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    private var bibleJob: Job? = null
    private var storyJob: Job? = null

    private var openingJob: Job? = null
    private var actionJob: Job? = null
    private var goalCheckJob: Job? = null
    private var endingJob: Job? = null
    private var epilogueJob: Job? = null
    private var typingJob: Job? = null
    private var polishJob: Job? = null

    private var openingKey: String? = null
    private var loadedVerifyKey: String? = null
    private var endingAutoKey: String? = null
    private var endingRequestId = 0
    private var epilogueRequestId = 0
    private var actionRunning = false
    private var goalCheckWarned = false
    private var settingsReturnScreen = Screen.HOME
    private var bibleReturnScreen = Screen.HOME
    private var epilogueReturnScreen = Screen.GAME

    init {
        val saved = settingsStore.read()
        val form = saved ?: initialSettingsForm()
        _settingsState.value = SettingsUiState(
            form = form,
            selectedPresetId = saved?.let { matchPreset(it) } ?: DEFAULT_PRESET_ID,
            params = form.toModelParamsInput(),
        )
        viewModelScope.launch { refreshProjects() }
    }

    // ------------------------------------------------------------------
    // 首页 / 存档
    // ------------------------------------------------------------------

    fun clearHomeMessage() {
        _homeState.update { it.copy(error = "") }
    }

    fun searchNovel(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            _homeState.update { it.copy(error = "请提供小说名称") }
            return
        }
        if (_homeState.value.isSearching) return

        _homeState.update { it.copy(isSearching = true, error = "", loadingProjectId = "") }
        viewModelScope.launch {
            try {
                val config = requireConfig()
                val data = searchService.searchNovel(config, name)
                if (data.searchFailed) {
                    _messages.tryEmit("部分联网检索失败，已用模型知识补全；结果可能缺少最新资料")
                }
                enterProject(createProjectFromSearch(data), saveImmediately = false)
                _saveState.value = SaveState.SAVING
                scheduleSave()
                startBibleGeneration(force = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _homeState.update {
                    it.copy(error = getAiErrorMessage(error, "搜索失败，请重试"))
                }
            } finally {
                _homeState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadSavedProject(id: String) {
        if (_homeState.value.loadingProjectId.isNotEmpty()) return
        _homeState.update { it.copy(loadingProjectId = id, error = "") }
        viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) { projectStore.read(id) }
                    ?: throw IllegalStateException("读取存档失败：文件不存在或内容损坏")
                applyLoadedProject(document)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _homeState.update {
                    it.copy(error = error.message?.takeIf { message -> message.isNotBlank() } ?: "读取存档失败")
                }
            } finally {
                _homeState.update { it.copy(loadingProjectId = "") }
            }
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readTextFromUri(uri) }
                val imported = parseImportedJson(text)
                val document = imported.project.copy(updatedAt = nowIso())
                applyImportedProject(document, imported.kind)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _messages.tryEmit(error.message?.takeIf { it.isNotBlank() } ?: "解析 JSON 失败")
            }
        }
    }

    fun suggestedExportName(): String = _project.value?.let { projectFileName(it) } ?: "假如是你-存档.json"

    fun exportProject(uri: Uri) {
        val document = _project.value ?: return
        viewModelScope.launch {
            try {
                val text = ProjectCodec.encodeProject(document, pretty = true) + "\n"
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("无法写入所选文件")
                    stream.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
                }
                _messages.tryEmit("已导出《${document.novelName}》")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _messages.tryEmit("导出失败：${error.message?.takeIf { it.isNotBlank() } ?: "无法写入文件"}")
            }
        }
    }

    private fun applyImportedProject(document: ProjectDocument, kind: ImportKind) {
        cancelAllJobs()
        resetTransientStates()
        _project.value = document
        _saveState.value = SaveState.SAVING
        viewModelScope.launch {
            val saved = persistDocument(document)
            if (saved) {
                _messages.tryEmit("已从${kind.label}导入《${document.novelName}》")
                refreshProjects()
            }
        }
        enterPhaseFor(document)
    }

    private fun applyLoadedProject(document: ProjectDocument) {
        cancelAllJobs()
        resetTransientStates()
        _project.value = document
        _saveState.value = SaveState.SAVING
        scheduleSave()
        enterPhaseFor(document)
    }

    private fun enterProject(document: ProjectDocument, saveImmediately: Boolean) {
        cancelAllJobs()
        resetTransientStates()
        _project.value = document
        _saveState.value = if (saveImmediately) SaveState.SAVING else SaveState.IDLE
        _screen.value = when (phaseForProject(document)) {
            ProjectPhase.SEARCH, ProjectPhase.BIBLE -> Screen.BIBLE
            ProjectPhase.GAME -> Screen.GAME
        }
        if (saveImmediately) scheduleSave()
    }

    private fun enterPhaseFor(document: ProjectDocument) {
        when (phaseForProject(document)) {
            ProjectPhase.SEARCH, ProjectPhase.BIBLE -> {
                _screen.value = Screen.BIBLE
                if (document.bible == null) startBibleGeneration(force = true)
            }
            ProjectPhase.GAME -> {
                _screen.value = Screen.GAME
                prepareGame()
            }
        }
    }

    fun goHome() {
        val document = _project.value
        cancelAllJobs()
        saveJob?.cancel()
        if (document != null) {
            viewModelScope.launch {
                persistDocument(document)
                refreshProjects()
            }
        }
        _project.value = null
        _saveState.value = SaveState.IDLE
        _screen.value = Screen.HOME
        resetTransientStates()
    }

    fun handleBack(): Boolean {
        return when (_screen.value) {
            Screen.HOME -> false
            Screen.SETTINGS -> {
                closeSettings()
                true
            }
            Screen.STORIES -> {
                closeStoryPicker()
                true
            }
            Screen.EPILOGUE -> {
                closeEpilogue()
                true
            }
            Screen.GAME -> {
                if (_project.value?.activeAdventureKey != null) {
                    if (!leaveBook()) {
                        _messages.tryEmit("正在生成，请稍候")
                        return true
                    }
                } else {
                    goHome()
                }
                true
            }
            Screen.BIBLE -> {
                closeBible()
                true
            }
        }
    }

    // ------------------------------------------------------------------
    // 设置
    // ------------------------------------------------------------------

    fun openSettings() {
        settingsReturnScreen = _screen.value
        val saved = settingsStore.read()
        val form = saved ?: initialSettingsForm()
        _settingsState.value = SettingsUiState(
            form = form,
            selectedPresetId = saved?.let { matchPreset(it) } ?: DEFAULT_PRESET_ID,
            params = form.toModelParamsInput(),
        )
        _screen.value = Screen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = settingsReturnScreen
    }

    fun selectPreset(presetId: String) {
        val preset = AI_PRESETS.firstOrNull { it.id == presetId } ?: return
        _settingsState.update { state ->
            state.copy(
                form = state.form.copy(
                    baseUrl = preset.baseUrl,
                    model = preset.model,
                    apiKey = preset.apiKey.orEmpty(),
                ),
                selectedPresetId = preset.id,
                models = emptyList(),
                notice = null,
            )
        }
    }

    fun setBaseUrl(value: String) = updateSettingsForm { it.copy(baseUrl = value) }

    fun setApiKey(value: String) = updateSettingsForm { it.copy(apiKey = value) }

    fun setModel(value: String) = updateSettingsForm { it.copy(model = value) }

    fun setTavilyKey(value: String) = updateSettingsForm { it.copy(tavilyApiKey = value) }

    fun setSearchProvider(provider: String) = updateSettingsForm { it.copy(searchProvider = provider) }

    fun setTemperature(value: String) =
        updateParams({ it.copy(temperature = value.trim().toDoubleOrNull()) }) {
            it.copy(temperature = value)
        }

    fun setTopP(value: String) =
        updateParams({ it.copy(topP = value.trim().toDoubleOrNull()) }) { it.copy(topP = value) }

    fun setMaxTokens(value: String) =
        updateParams({ it.copy(maxTokens = value.trim().toIntOrNull()) }) {
            it.copy(maxTokens = value)
        }

    fun setFrequencyPenalty(value: String) =
        updateParams({ it.copy(frequencyPenalty = value.trim().toDoubleOrNull()) }) {
            it.copy(frequencyPenalty = value)
        }

    fun setPresencePenalty(value: String) =
        updateParams({ it.copy(presencePenalty = value.trim().toDoubleOrNull()) }) {
            it.copy(presencePenalty = value)
        }

    fun setSeed(value: String) =
        updateParams({ it.copy(seed = value.trim().toIntOrNull()) }) { it.copy(seed = value) }

    fun setExtraParams(value: String) = updateParams({ it.copy(extraParams = value) }) {
        it.copy(extraParams = value)
    }

    fun resetModelParams() {
        updateParams(
            {
                it.copy(
                    temperature = null,
                    topP = null,
                    maxTokens = null,
                    frequencyPenalty = null,
                    presencePenalty = null,
                    seed = null,
                    extraParams = "",
                )
            },
            { ModelParamsInput() },
        )
    }

    private fun updateParams(
        transform: (AiConfig) -> AiConfig,
        input: (ModelParamsInput) -> ModelParamsInput,
    ) {
        _settingsState.update { state ->
            state.copy(form = transform(state.form), params = input(state.params))
        }
    }

    private fun updateSettingsForm(transform: (AiConfig) -> AiConfig) {
        _settingsState.update { state ->
            val next = transform(state.form)
            val selected = if (next.baseUrl != state.form.baseUrl || next.model != state.form.model) {
                "custom"
            } else {
                state.selectedPresetId
            }
            state.copy(form = next, selectedPresetId = selected)
        }
    }

    fun fetchModels() {
        val form = _settingsState.value.form.trimmed()
        if (form.baseUrl.isEmpty()) {
            _settingsState.update { it.copy(notice = Notice(NoticeKind.ERROR, "请填写 Base URL")) }
            return
        }
        try {
            normalizeBaseUrl(form.baseUrl)
        } catch (error: AiConfigurationException) {
            _settingsState.update {
                it.copy(notice = Notice(NoticeKind.ERROR, getAiErrorMessage(error, "Base URL 格式不正确")))
            }
            return
        }
        if (_settingsState.value.busy != null) return
        _settingsState.update { it.copy(busy = SettingsBusy.MODELS, notice = null) }
        viewModelScope.launch {
            try {
                val models = aiClient.listModels(form)
                _settingsState.update {
                    it.copy(
                        models = models,
                        notice = Notice(
                            NoticeKind.SUCCESS,
                            if (models.isEmpty()) "连接成功，但服务未返回模型列表" else "已获取 ${models.size} 个模型",
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _settingsState.update {
                    it.copy(notice = Notice(NoticeKind.ERROR, getAiErrorMessage(error, "获取模型列表失败")))
                }
            } finally {
                _settingsState.update { it.copy(busy = null) }
            }
        }
    }

    fun testConnection() {
        val form = _settingsState.value.form.trimmed()
        val validation = validateSettings(form)
        if (validation != null) {
            _settingsState.update { it.copy(notice = Notice(NoticeKind.ERROR, validation)) }
            return
        }
        if (_settingsState.value.busy != null) return
        _settingsState.update { it.copy(busy = SettingsBusy.TEST, notice = null) }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                val (reply, _) = aiClient.testConnection(form)
                val elapsed = System.currentTimeMillis() - startedAt
                val extra = if (reply.isNotBlank()) "，模型回复：$reply" else ""
                _settingsState.update {
                    it.copy(notice = Notice(NoticeKind.SUCCESS, "连接成功（${elapsed} ms）$extra"))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _settingsState.update {
                    it.copy(notice = Notice(NoticeKind.ERROR, getAiErrorMessage(error, "AI 连接测试失败")))
                }
            } finally {
                _settingsState.update { it.copy(busy = null) }
            }
        }
    }

    fun saveSettings() {
        var form = _settingsState.value.form.trimmed()
        val validation = validateSettings(form)
        if (validation != null) {
            _settingsState.update { it.copy(notice = Notice(NoticeKind.ERROR, validation)) }
            return
        }
        // 保存前规范化并写回表单，避免同一地址被重复拼接 /v1。
        form = try {
            form.copy(baseUrl = normalizeBaseUrl(form.baseUrl))
        } catch (error: AiConfigurationException) {
            _settingsState.update {
                it.copy(notice = Notice(NoticeKind.ERROR, getAiErrorMessage(error, "Base URL 格式不正确")))
            }
            return
        }
        settingsStore.write(form)
        _settingsState.update {
            it.copy(
                form = form,
                params = form.toModelParamsInput(),
                notice = Notice(NoticeKind.SUCCESS, "设置已保存到本机"),
            )
        }
    }

    fun clearSettings() {
        settingsStore.clear()
        _settingsState.update {
            it.copy(
                form = emptyAiConfig(),
                selectedPresetId = "custom",
                models = emptyList(),
                notice = Notice(NoticeKind.INFO, "已清除本机 AI 配置"),
                params = ModelParamsInput(),
            )
        }
    }

    private fun validateSettings(config: AiConfig): String? {
        if (config.baseUrl.isBlank()) return "请填写 Base URL"
        if (config.model.isBlank()) return "请填写模型名称"
        validateParamText()?.let { return it }
        try {
            normalizeBaseUrl(config.baseUrl)
        } catch (error: AiConfigurationException) {
            return getAiErrorMessage(error, "Base URL 格式不正确")
        }
        return validateAiParams(config)
    }

    /** 输入框里的中间态（例如只敲了一个「-」）不能悄悄当成「未设置」。 */
    private fun validateParamText(): String? {
        val params = _settingsState.value.params
        fun check(name: String, raw: String, integer: Boolean = false): String? {
            if (raw.isBlank()) return null
            val valid = if (integer) {
                raw.trim().toIntOrNull() != null
            } else {
                raw.trim().toDoubleOrNull() != null
            }
            return if (valid) null else "$name 需要填写数字，留空则使用场景默认值"
        }
        return check("Temperature", params.temperature)
            ?: check("Top P", params.topP)
            ?: check("最大输出 Token", params.maxTokens, integer = true)
            ?: check("Frequency Penalty", params.frequencyPenalty)
            ?: check("Presence Penalty", params.presencePenalty)
            ?: check("Seed", params.seed, integer = true)
    }

    private fun matchPreset(config: AiConfig): String =
        AI_PRESETS.firstOrNull { it.baseUrl == config.baseUrl && it.model == config.model }?.id ?: "custom"

    // ------------------------------------------------------------------
    // 设定圣经
    // ------------------------------------------------------------------

    fun startBibleGeneration(force: Boolean = false) {
        if (!force && bibleJob?.isActive == true) return
        val project = _project.value ?: return
        if (project.bible != null) return

        if (force) bibleJob?.cancel()
        val projectId = project.id
        val searchData = project.search ?: SearchData(
            novelName = project.novelName,
            summary = "",
            results = emptyList(),
        )
        _bibleState.update { it.copy(isGenerating = true, rawText = "", error = "") }

        bibleJob = viewModelScope.launch {
            try {
                val config = requireConfig()
                val novelName = searchData.novelName.ifBlank { project.novelName }
                var accumulated = ""
                aiClient.streamChat(
                    config = config,
                    messages = Prompts.bibleMessages(novelName, searchData.results, searchData.summary),
                    temperature = 0.5,
                    maxTokens = 6000,
                    fallbackLabel = "生成设定圣经失败",
                    failOnTruncation = true,
                ).collect { chunk ->
                    accumulated += chunk.content
                    _bibleState.update { it.copy(rawText = accumulated) }
                }
                if (_project.value?.id != projectId) return@launch

                val bible = normalizeBible(parseJsonLoose(accumulated), novelName)
                if (bible != null) {
                    updateProject { it.copy(bible = bible) }
                    _bibleState.update { it.copy(isGenerating = false, error = "") }
                } else {
                    val message = if (accumulated.isBlank()) {
                        "AI 服务未返回内容，请检查服务配置后重试"
                    } else {
                        "AI 返回的设定圣经无法解析，请点击重试"
                    }
                    _bibleState.update { it.copy(isGenerating = false, error = message) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_project.value?.id == projectId) {
                    _bibleState.update {
                        it.copy(
                            isGenerating = false,
                            error = getAiErrorMessage(error, "生成设定圣经失败，请检查 AI 服务配置后重试"),
                        )
                    }
                }
            }
        }
    }

    fun retryBible() {
        _bibleState.update { it.copy(attempt = it.attempt + 1, error = "") }
        startBibleGeneration(force = true)
    }

    /** 从冒险页回看已生成的设定圣经，不触发重新生成。 */
    fun openBible() {
        if (_project.value?.bible == null) return
        if (_screen.value != Screen.BIBLE) bibleReturnScreen = _screen.value
        _screen.value = Screen.BIBLE
    }

    /** 从故事栏 / 冒险打开圣经时，返回原页面；从书架进入时则回书架。 */
    fun closeBible() {
        when (bibleReturnScreen) {
            Screen.GAME, Screen.STORIES -> _screen.value = bibleReturnScreen
            else -> goHome()
        }
    }

    fun startGameScreen() {
        if (_project.value?.bible == null) return
        _screen.value = Screen.GAME
        prepareGame()
    }

    // ------------------------------------------------------------------
    // 故事书
    // ------------------------------------------------------------------

    /** 选中角色后先进入故事栏；故事栏已生成过就直接复用。 */
    fun openStoryPicker(character: CharacterChoice) {
        if (character.name.isBlank()) return
        if (_gameState.value.isInitializing) return
        cancelGameJobs()
        resetGameUi()
        _gameState.value = GameUiState(pickerCharacter = character)
        _screen.value = Screen.STORIES

        val document = _project.value ?: return
        val key = characterKey(character)
        if (document.storyOptions[key].isNullOrEmpty()) {
            generateStoryOptions(character)
        }
    }

    fun closeStoryPicker() {
        storyJob?.cancel()
        storyJob = null
        resetGameUi()
        _screen.value = Screen.GAME
    }

    fun retryStoryOptions() {
        val character = _gameState.value.pickerCharacter ?: return
        generateStoryOptions(character)
    }

    private fun generateStoryOptions(character: CharacterChoice) {
        val document = _project.value ?: return
        val bible = document.bible ?: return
        if (_gameState.value.isGeneratingStories) return
        val projectId = document.id
        val key = characterKey(character)
        storyJob?.cancel()
        _gameState.update {
            it.copy(isGeneratingStories = true, storyError = "")
        }

        storyJob = viewModelScope.launch {
            try {
                val config = requireConfig()
                val raw = aiClient.chatOnce(
                    config = config,
                    messages = Prompts.storyOptionMessages(bible, character),
                    temperature = 0.95,
                    maxTokens = 1800,
                    callTimeoutSeconds = 90,
                    fallbackLabel = "构思故事栏失败",
                    failOnTruncation = true,
                )
                val options = normalizeStoryOptions(parseJsonLoose(raw))
                if (options.size < 2) {
                    throw AiServiceException("AI 返回的故事线不足，请重新构思")
                }
                if (_project.value?.id != projectId) return@launch
                updateProject { current ->
                    current.copy(storyOptions = current.storyOptions + (key to options))
                }
                _gameState.update {
                    if (it.pickerCharacter == character) {
                        it.copy(isGeneratingStories = false, storyError = "")
                    } else {
                        it.copy(isGeneratingStories = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_project.value?.id == projectId) {
                    _gameState.update {
                        if (it.pickerCharacter == character) {
                            it.copy(
                                isGeneratingStories = false,
                                storyError = getAiErrorMessage(error, "构思故事栏失败，请检查 AI 设置后重试"),
                            )
                        } else {
                            it.copy(isGeneratingStories = false)
                        }
                    }
                }
            }
        }
    }

    /** 从故事栏选中一条故事线；已有进度则直接续上。 */
    fun startStory(character: CharacterChoice, story: StoryOption) {
        if (character.name.isBlank() || story.id.isBlank()) return
        if (_gameState.value.isInitializing) return
        cancelGameJobs()
        resetGameUi()
        val key = storyKey(character, story.id)
        updateProject { document ->
            if (document.adventures.containsKey(key)) {
                document.copy(activeAdventureKey = key)
            } else {
                document.copy(
                    adventures = document.adventures + (key to createAdventure(character, story)),
                    activeAdventureKey = key,
                )
            }
        }
        _screen.value = Screen.GAME
        prepareGame()
    }

    fun openBook(key: String) {
        val document = _project.value ?: return
        if (!document.adventures.containsKey(key)) return
        if (_gameState.value.isInitializing) return
        cancelGameJobs()
        resetGameUi()
        updateProject { it.copy(activeAdventureKey = key) }
        _screen.value = Screen.GAME
        prepareGame()
    }

    /** 返回角色选择；生成中不允许离开，与原项目「切换角色」按钮的禁用逻辑一致。 */
    fun leaveBook(): Boolean {
        if (_gameState.value.busy) return false
        cancelGameJobs()
        updateProject { it.copy(activeAdventureKey = null) }
        resetGameUi()
        _screen.value = Screen.GAME
        return true
    }

    /** 回到当前角色的故事栏，方便换一条命运继续。 */
    fun backToStoryPicker() {
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        if (_gameState.value.busy) return
        cancelGameJobs()
        updateProject { it.copy(activeAdventureKey = null) }
        openStoryPicker(book.character)
    }

    fun restartBook() {
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        if (_gameState.value.busy) return
        cancelGameJobs()
        updateProject { current ->
            val target = current.adventures[key] ?: return@updateProject current
            current.copy(
                adventures = current.adventures + (key to createAdventure(target.character, target.story)),
            )
        }
        resetGameUi()
        openingKey = null
        loadedVerifyKey = null
        endingAutoKey = null
        _screen.value = Screen.GAME
        prepareGame()
    }

    fun retryOpening() {
        _gameState.update { it.copy(error = "") }
        openingKey = null
        prepareGame()
    }

    private fun prepareGame() {
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        if (book.gameState == null) {
            startOpening(key, book)
        } else {
            maybeVerifyLoadedBook()
            maybeGenerateEndingSummary()
        }
    }

    private fun startOpening(key: String, book: AdventureState) {
        val initKey = "${book.character.name}:${book.startedAt}"
        if (openingKey == initKey && openingJob?.isActive == true) return
        openingKey = initKey
        openingJob?.cancel()
        val projectId = _project.value?.id
        _gameState.update {
            it.copy(
                isInitializing = true,
                error = "",
                narration = "",
                displayedText = "",
                isTyping = false,
            )
        }
        typingJob?.cancel()

        openingJob = viewModelScope.launch {
            try {
                val config = requireConfig()
                val document = _project.value ?: return@launch
                val bible = document.bible
                    ?: throw AiServiceException("缺少设定圣经数据")
                var accumulated = ""
                aiClient.streamChat(
                    config = config,
                    messages = Prompts.gameMessages(bible, book.character, book.story),
                    temperature = 0.9,
                    maxTokens = 2048,
                    fallbackLabel = "生成游戏初始场景失败",
                    failOnTruncation = true,
                ).collect { chunk -> accumulated += chunk.content }

                if (_project.value?.id != projectId) return@launch

                val opening = normalizeGameOpening(parseJsonLoose(accumulated), book.story)
                if (opening == null) {
                    val message = if (accumulated.isBlank()) {
                        "AI 服务未返回内容，请检查服务配置后重试"
                    } else {
                        "AI 返回的开场不完整（缺少场景、选项或最终目标），请重新生成开场"
                    }
                    _gameState.update { it.copy(error = message) }
                    openingKey = null
                    return@launch
                }

                val initialStats = opening.gameState.playerCharacter.initialStats
                val nextState = opening.gameState.copy(
                    playerCharacter = opening.gameState.playerCharacter.copy(
                        name = book.character.name,
                        background = book.character.background.ifBlank {
                            opening.gameState.playerCharacter.background
                        },
                        initialStats = initialStats,
                    ),
                )
                val introText = joinNarration(
                    opening.gameState.intro,
                    opening.gameState.currentScene.description,
                )
                updateBook(key) { current ->
                    if (current.startedAt != book.startedAt) return@updateBook current
                    current.copy(
                        gameState = nextState,
                        stats = initialStats,
                        history = listOf(HistoryEntry(HistoryType.NARRATION, introText)),
                        isEnding = false,
                        endingText = "",
                        turn = 0,
                        goal = opening.goal,
                        timeline = opening.timeline,
                        outcome = AdventureOutcome.PLAYING,
                        outcomeReason = "",
                        endingSummary = "",
                        epilogue = "",
                    )
                }
                if (isActiveBook(book)) {
                    resetCustomDraft()
                    startTypewriter(introText)
                }
                loadedVerifyKey = null
                endingAutoKey = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_project.value?.id == projectId) {
                    _gameState.update {
                        it.copy(
                            error = getAiErrorMessage(
                                error,
                                "生成游戏初始场景失败，请检查 AI 服务配置后重试",
                            ),
                        )
                    }
                }
                openingKey = null
            } finally {
                _gameState.update { it.copy(isInitializing = false) }
            }
        }
    }

    fun choose(choiceId: String, choiceText: String) {
        if (_gameState.value.isProcessing || _gameState.value.isInitializing || actionRunning) return
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        val game = book.gameState ?: return
        val config = settingsStore.read()
        if (config == null) {
            _gameState.update { it.copy(error = NOT_CONFIGURED_MESSAGE) }
            return
        }

        goalCheckJob?.cancel()
        val projectId = document.id
        val snapshot = snapshotOf(book, game)
        val historyWithChoice = book.history + HistoryEntry(HistoryType.CHOICE, choiceText)
        actionRunning = true
        typingJob?.cancel()
        _gameState.update {
            it.copy(
                isProcessing = true,
                isVerifyingGoal = false,
                error = "",
                narration = "",
                displayedText = "",
                isTyping = false,
                polishError = "",
            )
        }
        updateBook(key) { it.copy(history = historyWithChoice) }

        actionJob = viewModelScope.launch {
            try {
                var accumulated = ""
                aiClient.streamChat(
                    config = config,
                    messages = Prompts.gameActionMessages(
                        bible = document.bible ?: throw AiServiceException("缺少设定圣经数据"),
                        gameState = game,
                        stats = book.stats,
                        goal = book.goal,
                        timeline = book.timeline,
                        choiceId = choiceId,
                        choiceText = choiceText,
                        character = book.character,
                        history = book.history.takeLast(10),
                        story = book.story,
                    ),
                    temperature = 0.9,
                    maxTokens = 1600,
                    fallbackLabel = "处理游戏动作失败",
                    failOnTruncation = true,
                ).collect { chunk -> accumulated += chunk.content }

                if (_project.value?.id != projectId) return@launch

                val result = normalizeSceneResult(parseJsonLoose(accumulated))
                if (result == null) {
                    restoreBook(key, book)
                    val message = if (accumulated.isBlank()) {
                        "AI 服务未返回内容，请检查服务配置后重试"
                    } else {
                        "AI 返回的下一场景无法解析，请重新选择该选项"
                    }
                    _gameState.update { it.copy(error = message) }
                    return@launch
                }

                val roundText = listOf(result.narration, result.endingText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                var goalAchieved = result.goalAchieved || result.endingType == EndingType.SUCCESS
                if (!goalAchieved) {
                    _gameState.update { it.copy(isVerifyingGoal = true) }
                    val verdict = requestGoalCheck(
                        goal = book.goal,
                        narration = roundText,
                        scene = result.currentScene,
                        history = historyWithChoice.takeLast(8),
                    )
                    _gameState.update { it.copy(isVerifyingGoal = false) }
                    goalAchieved = verdict?.achieved == true ||
                        (verdict == null && detectGoalAchievedFromText(roundText))
                }

                val effective = if (goalAchieved) {
                    result.copy(goalAchieved = true, isEnding = true, endingType = EndingType.SUCCESS)
                } else {
                    result
                }
                val finalTurn = effective.isEnding || effective.goalAchieved || effective.endingType != null
                val nextStats = applyStatChange(book.stats, effective.statsChange, final = finalTurn)
                val resolution = resolveOutcome(book, nextStats, effective)
                val ended = resolution.outcome != AdventureOutcome.PLAYING
                val narrationText = joinNarration(
                    effective.narration.ifBlank { effective.endingText },
                    effective.currentScene.description,
                )

                updateBook(key) { current ->
                    if (current.startedAt != book.startedAt) return@updateBook current
                    current.copy(
                        gameState = if (ended) {
                            book.gameState
                        } else {
                            current.gameState?.copy(currentScene = effective.currentScene) ?: book.gameState
                        },
                        stats = nextStats,
                        history = book.history + HistoryEntry(HistoryType.CHOICE, choiceText) +
                            HistoryEntry(HistoryType.NARRATION, narrationText),
                        isEnding = ended,
                        endingText = if (ended) effective.endingText else "",
                        turn = book.turn + 1,
                        snapshots = (book.snapshots + snapshot).takeLast(MAX_UNDO_SNAPSHOTS),
                        timeline = resolution.timeline,
                        outcome = resolution.outcome,
                        outcomeReason = resolution.reason,
                        endingSummary = "",
                        epilogue = "",
                    )
                }
                if (isActiveBook(book)) {
                    resetCustomDraft()
                    startTypewriter(narrationText)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_project.value?.id == projectId) {
                    restoreBook(key, book)
                    _gameState.update {
                        it.copy(
                            error = getAiErrorMessage(
                                error,
                                "处理游戏动作失败，请检查 AI 服务配置后重试",
                            ),
                        )
                    }
                }
            } finally {
                actionRunning = false
                _gameState.update {
                    it.copy(isProcessing = false, isVerifyingGoal = false)
                }
                if (isActiveBook(book) && activeBook()?.outcome != AdventureOutcome.PLAYING) {
                    maybeGenerateEndingSummary()
                }
            }
        }
    }

    private suspend fun requestGoalCheck(
        goal: com.ifitwereyou.character.data.StoryGoal,
        narration: String,
        scene: com.ifitwereyou.character.data.Scene?,
        history: List<HistoryEntry>,
    ): com.ifitwereyou.character.data.GoalCheckVerdict? {
        val saved = settingsStore.read() ?: return null
        // 判定任务预算固定为 512，不跟随用户的创作输出上限。
        val config = saved.withoutSamplingOverrides().copy(maxTokens = null)
        return try {
            val raw = withTimeout(GOAL_CHECK_TIMEOUT_MS) {
                aiClient.chatOnce(
                    config = config,
                    messages = Prompts.goalCheckMessages(goal, narration, scene, history),
                    temperature = 0.0,
                    maxTokens = 512,
                    callTimeoutSeconds = 20,
                    fallbackLabel = "判定最终目标失败",
                )
            }
            normalizeGoalCheck(parseJsonLoose(raw))
        } catch (_: TimeoutCancellationException) {
            warnGoalCheckFallback("目标判定超时，已改用文本线索判断")
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            warnGoalCheckFallback("目标判定调用失败，已改用文本线索判断")
            null
        }
    }

    private fun warnGoalCheckFallback(message: String) {
        if (goalCheckWarned) return
        goalCheckWarned = true
        _messages.tryEmit(message)
    }

    /** 打开旧存档时补做一次目标复核，避免「目标早已完成」的局继续下去。 */
    fun maybeVerifyLoadedBook() {
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        val game = book.gameState ?: return
        if (book.outcome != AdventureOutcome.PLAYING || book.turn == 0) return
        if (_gameState.value.isProcessing || actionRunning) return

        val guardKey = "${book.character.name}:${book.startedAt}"
        if (loadedVerifyKey == guardKey) return
        val lastNarration = book.history.lastOrNull { it.type == HistoryType.NARRATION } ?: return
        loadedVerifyKey = guardKey

        goalCheckJob?.cancel()
        goalCheckJob = viewModelScope.launch {
            // 读档复核只是后台兜底，不阻塞玩家看选项或继续行动。
            _gameState.update { it.copy(isVerifyingGoal = true) }
            try {
                val verdict = requestGoalCheck(
                    goal = book.goal,
                    narration = lastNarration.text,
                    scene = game.currentScene,
                    history = book.history.takeLast(8),
                )
                if (verdict?.achieved == true) {
                    updateBook(key) { current ->
                        if (current.startedAt != book.startedAt) return@updateBook current
                        current.copy(
                            isEnding = true,
                            outcome = AdventureOutcome.SUCCESS,
                            outcomeReason = "达成最终目标：${current.goal.title}",
                        )
                    }
                }
            } finally {
                _gameState.update { it.copy(isVerifyingGoal = false) }
                if (activeBook()?.outcome != AdventureOutcome.PLAYING) {
                    maybeGenerateEndingSummary()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 打字机
    // ------------------------------------------------------------------

    private fun startTypewriter(text: String) {
        typingJob?.cancel()
        if (text.isEmpty()) {
            _gameState.update {
                it.copy(narration = "", displayedText = "", isTyping = false)
            }
            return
        }
        _gameState.update {
            it.copy(narration = text, displayedText = "", isTyping = true)
        }
        typingJob = viewModelScope.launch {
            delay(TYPING_START_DELAY_MS)
            var index = 0
            while (index < text.length) {
                index++
                _gameState.update { it.copy(displayedText = text.substring(0, index)) }
                delay(TYPING_INTERVAL_MS)
            }
            _gameState.update { it.copy(isTyping = false) }
        }
    }

    fun skipTyping() {
        typingJob?.cancel()
        _gameState.update {
            it.copy(displayedText = it.narration, isTyping = false)
        }
    }

    // ------------------------------------------------------------------
    // 结局页总结
    // ------------------------------------------------------------------

    fun maybeGenerateEndingSummary() {
        val book = activeBook() ?: return
        if (book.outcome == AdventureOutcome.PLAYING) return
        if (book.gameState == null || book.turn == 0 || book.endingSummary.isNotBlank()) return
        if (endingJob?.isActive == true) return
        val autoKey = "${book.character.name}:${book.startedAt}:${book.turn}:${book.outcome.wire}"
        if (endingAutoKey == autoKey) return
        endingAutoKey = autoKey
        writeEndingSummary(book, force = false)
    }

    fun retryEndingSummary() {
        val book = activeBook() ?: return
        if (book.outcome == AdventureOutcome.PLAYING) return
        endingAutoKey = null
        writeEndingSummary(book, force = true)
    }

    private fun writeEndingSummary(source: AdventureState, force: Boolean) {
        if (force) endingJob?.cancel()
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val bible = document.bible ?: return
        if (source.gameState == null) return

        val requestId = ++endingRequestId
        val projectId = document.id
        _gameState.update {
            it.copy(isWritingEnding = true, endingError = "", endingDraft = "")
        }

        endingJob = viewModelScope.launch {
            try {
                val config = requireConfig()
                var accumulated = ""
                withTimeout(ENDING_TIMEOUT_MS) {
                    aiClient.streamChat(
                        config = config,
                        messages = Prompts.endingSummaryMessages(
                            bible = bible,
                            character = source.character,
                            story = source.story,
                            goal = source.goal,
                            timeline = source.timeline,
                            outcome = source.outcome.wire,
                            outcomeReason = source.outcomeReason,
                            endingText = source.endingText.ifBlank {
                                source.history.lastOrNull { it.type == HistoryType.NARRATION }?.text.orEmpty()
                            },
                            history = source.history,
                        ),
                        temperature = 0.72,
                        maxTokens = 1400,
                        callTimeoutSeconds = 120,
                        fallbackLabel = "书写结局总结失败",
                    ).collect { chunk ->
                        accumulated += chunk.content
                        if (requestId == endingRequestId) {
                            _gameState.update { it.copy(endingDraft = accumulated) }
                        }
                    }
                }

                val summary = accumulated.trim()
                if (summary.isEmpty()) throw AiServiceException("AI 未返回结局总结，请重试")
                if (requestId != endingRequestId) return@launch

                val current = _project.value
                val currentBook = current?.adventures?.get(key)
                if (current?.id != projectId ||
                    currentBook == null ||
                    currentBook.startedAt != source.startedAt ||
                    currentBook.turn != source.turn ||
                    currentBook.outcome != source.outcome
                ) {
                    return@launch
                }

                updateBook(key) { book ->
                    if (book.startedAt != source.startedAt) return@updateBook book
                    book.copy(endingSummary = summary)
                }
                _gameState.update { it.copy(endingDraft = "") }
            } catch (error: TimeoutCancellationException) {
                if (requestId == endingRequestId) {
                    _gameState.update { it.copy(endingError = "结局总结生成超时，可以再试一次") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (requestId == endingRequestId) {
                    _gameState.update {
                        it.copy(
                            endingError = getAiErrorMessage(
                                error,
                                "结局总结生成失败，请检查 AI 配置后重试",
                            ),
                        )
                    }
                }
            } finally {
                if (requestId == endingRequestId) {
                    _gameState.update { it.copy(isWritingEnding = false) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 后日谈（可选）
    // ------------------------------------------------------------------

    /** 从结局页打开后日谈：已有内容直接阅读，没有则按需生成。 */
    fun openEpilogue() {
        val book = activeBook() ?: return
        if (book.outcome == AdventureOutcome.PLAYING) return
        epilogueReturnScreen = Screen.GAME
        _screen.value = Screen.EPILOGUE
        if (book.epilogue.isBlank() && epilogueJob?.isActive != true) {
            writeEpilogue(book, force = false)
        }
    }

    /** 从故事栏打开某条故事的后日谈。 */
    fun openEpilogueForBook(key: String) {
        val document = _project.value ?: return
        val book = document.adventures[key] ?: return
        if (book.outcome == AdventureOutcome.PLAYING) return
        cancelGameJobs()
        resetGameUi()
        epilogueReturnScreen = Screen.STORIES
        updateProject { it.copy(activeAdventureKey = key) }
        _screen.value = Screen.EPILOGUE
        val latest = _project.value?.adventures?.get(key) ?: book
        if (latest.epilogue.isBlank() && epilogueJob?.isActive != true) {
            writeEpilogue(latest, force = false)
        }
    }

    fun closeEpilogue() {
        epilogueRequestId++
        epilogueJob?.cancel()
        _gameState.update {
            it.copy(
                epilogueDraft = "",
                epilogueError = "",
                isWritingEpilogue = false,
            )
        }
        val book = activeBook()
        if (epilogueReturnScreen == Screen.STORIES && book != null) {
            updateProject { it.copy(activeAdventureKey = null) }
            resetGameUi()
            _gameState.value = GameUiState(pickerCharacter = book.character)
            _screen.value = Screen.STORIES
        } else {
            epilogueReturnScreen = Screen.GAME
            _screen.value = Screen.GAME
            maybeGenerateEndingSummary()
        }
    }

    fun rewriteEpilogue() {
        val book = activeBook() ?: return
        if (book.outcome == AdventureOutcome.PLAYING) return
        writeEpilogue(book, force = true)
    }

    private fun writeEpilogue(source: AdventureState, force: Boolean) {
        if (force) epilogueJob?.cancel()
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val bible = document.bible ?: return
        if (source.goal.title.isBlank()) return

        val requestId = ++epilogueRequestId
        val projectId = document.id
        _gameState.update {
            it.copy(isWritingEpilogue = true, epilogueError = "", epilogueDraft = "")
        }

        epilogueJob = viewModelScope.launch {
            try {
                val config = requireConfig()
                var accumulated = ""
                withTimeout(EPILOGUE_TIMEOUT_MS) {
                    aiClient.streamChat(
                        config = config,
                        messages = Prompts.epilogueMessages(
                            bible = bible,
                            character = source.character,
                            goal = source.goal,
                            timeline = source.timeline,
                            outcome = source.outcome.wire,
                            outcomeReason = source.outcomeReason,
                            endingText = source.endingText,
                            history = source.history,
                            endingSummary = source.endingSummary,
                        ),
                        temperature = 0.85,
                        maxTokens = 1600,
                        callTimeoutSeconds = 120,
                        fallbackLabel = "书写后日谈失败",
                    ).collect { chunk ->
                        accumulated += chunk.content
                        if (requestId == epilogueRequestId) {
                            _gameState.update { it.copy(epilogueDraft = accumulated) }
                        }
                    }
                }

                val epilogue = accumulated.trim()
                if (epilogue.isEmpty()) throw AiServiceException("AI 未返回后日谈内容，请重试")
                if (requestId != epilogueRequestId) return@launch

                val current = _project.value
                val currentBook = current?.adventures?.get(key)
                if (current?.id != projectId ||
                    currentBook == null ||
                    currentBook.startedAt != source.startedAt ||
                    currentBook.turn != source.turn ||
                    currentBook.outcome != source.outcome
                ) {
                    return@launch
                }

                updateBook(key) { book ->
                    if (book.startedAt != source.startedAt) return@updateBook book
                    book.copy(epilogue = epilogue)
                }
                _gameState.update { it.copy(epilogueDraft = "") }
            } catch (error: TimeoutCancellationException) {
                if (requestId == epilogueRequestId) {
                    _gameState.update { it.copy(epilogueError = "书写后日谈超时，可以再试一次") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (requestId == epilogueRequestId) {
                    _gameState.update {
                        it.copy(
                            epilogueError = getAiErrorMessage(
                                error,
                                "书写后日谈失败，请检查 AI 服务配置后重试",
                            ),
                        )
                    }
                }
            } finally {
                if (requestId == epilogueRequestId) {
                    _gameState.update { it.copy(isWritingEpilogue = false) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 回退与自填行动
    // ------------------------------------------------------------------

    fun undo() {
        if (_gameState.value.busy) return
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        val snapshot = book.snapshots.lastOrNull() ?: return

        endingRequestId++
        endingJob?.cancel()
        epilogueRequestId++
        epilogueJob?.cancel()
        goalCheckJob?.cancel()
        endingAutoKey = null
        polishJob?.cancel()
        typingJob?.cancel()
        _gameState.update {
            it.copy(
                endingDraft = "",
                endingError = "",
                isWritingEnding = false,
                epilogueDraft = "",
                epilogueError = "",
                isWritingEpilogue = false,
                narration = "",
                displayedText = "",
                isTyping = false,
                error = "",
                customText = "",
                originalCustomText = "",
                isPolishing = false,
                polishError = "",
            )
        }
        updateBook(key) { current ->
            if (current.startedAt != book.startedAt) return@updateBook current
            current.copy(
                gameState = snapshot.gameState,
                stats = snapshot.stats,
                history = snapshot.history,
                isEnding = snapshot.isEnding,
                endingText = snapshot.endingText,
                turn = snapshot.turn,
                snapshots = current.snapshots.dropLast(1),
                timeline = snapshot.timeline,
                outcome = snapshot.outcome,
                outcomeReason = snapshot.outcomeReason,
                endingSummary = "",
                epilogue = "",
            )
        }
    }

    fun updateCustomText(value: String) {
        _gameState.update {
            it.copy(customText = value.take(MAX_CUSTOM_CHARS), polishError = "")
        }
    }

    fun polishCustomAction() {
        if (_gameState.value.isPolishing) return
        val text = _gameState.value.customText.trim()
        if (text.length < 2) return
        if (text.length > MAX_CUSTOM_CHARS) {
            _gameState.update { it.copy(polishError = "行动描述最多 $MAX_CUSTOM_CHARS 字") }
            return
        }
        val document = _project.value ?: return
        val key = document.activeAdventureKey ?: return
        val book = document.adventures[key] ?: return
        val game = book.gameState ?: return
        val config = settingsStore.read()
        if (config == null) {
            _gameState.update { it.copy(polishError = NOT_CONFIGURED_MESSAGE) }
            return
        }

        polishJob?.cancel()
        _gameState.update { it.copy(isPolishing = true, polishError = "") }
        polishJob = viewModelScope.launch {
            try {
                val raw = withTimeout(POLISH_TIMEOUT_MS) {
                    aiClient.chatOnce(
                        config = config.copy(maxTokens = null),
                        messages = Prompts.polishMessages(
                            bible = document.bible ?: SettingBible(),
                            character = book.character,
                            goal = book.goal,
                            scene = game.currentScene,
                            text = text,
                            history = book.history.takeLast(6),
                        ),
                        temperature = 0.6,
                        maxTokens = 300,
                        callTimeoutSeconds = 30,
                        fallbackLabel = "润色行动失败",
                    )
                }
                val polished = Prompts.cleanPolish(raw)
                if (polished.isEmpty()) throw AiServiceException("AI 没有给出润色结果，请重试")
                _gameState.update {
                    it.copy(
                        originalCustomText = text,
                        customText = polished,
                        isPolishing = false,
                        polishError = "",
                    )
                }
            } catch (error: TimeoutCancellationException) {
                _gameState.update {
                    it.copy(isPolishing = false, polishError = "润色超时，可以再试一次")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _gameState.update {
                    it.copy(
                        isPolishing = false,
                        polishError = getAiErrorMessage(error, "润色失败，请检查 AI 服务配置后重试"),
                    )
                }
            }
        }
    }

    fun restoreCustomText() {
        _gameState.update { state ->
            if (state.originalCustomText.isEmpty()) state
            else state.copy(
                customText = state.originalCustomText,
                originalCustomText = "",
                polishError = "",
            )
        }
    }

    private fun resetCustomDraft() {
        polishJob?.cancel()
        _gameState.update {
            it.copy(
                customText = "",
                originalCustomText = "",
                isPolishing = false,
                polishError = "",
            )
        }
    }

    // ------------------------------------------------------------------
    // 存档落盘
    // ------------------------------------------------------------------

    private fun scheduleSave() {
        saveJob?.cancel()
        _saveState.value = SaveState.SAVING
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveCurrent()
        }
    }

    private suspend fun saveCurrent() {
        val document = _project.value ?: return
        saveMutex.withLock {
            try {
                val saved = withContext(Dispatchers.IO) { projectStore.save(document) }
                if (_project.value?.id == saved.id) {
                    _project.value = _project.value?.copy(updatedAt = saved.updatedAt)
                }
                _saveState.value = SaveState.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _saveState.value = SaveState.ERROR
            }
        }
    }

    private suspend fun persistDocument(document: ProjectDocument): Boolean {
        return saveMutex.withLock {
            try {
                withContext(Dispatchers.IO) { projectStore.save(document) }
                _saveState.value = SaveState.SAVED
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _saveState.value = SaveState.ERROR
                false
            }
        }
    }

    fun flushSave() {
        val document = _project.value ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            persistDocument(document)
            refreshProjects()
        }
    }

    private fun updateProject(transform: (ProjectDocument) -> ProjectDocument) {
        val current = _project.value ?: return
        val next = transform(current)
        if (next == current) return
        _project.value = next.copy(updatedAt = nowIso())
        scheduleSave()
    }

    private fun updateBook(key: String, transform: (AdventureState) -> AdventureState) {
        updateProject { document ->
            val book = document.adventures[key] ?: return@updateProject document
            val next = transform(book)
            if (next == book) return@updateProject document
            document.copy(adventures = document.adventures + (key to next))
        }
    }

    private fun restoreBook(key: String, book: AdventureState) {
        updateBook(key) { book }
    }

    private fun activeBook(): AdventureState? {
        val document = _project.value ?: return null
        val key = document.activeAdventureKey ?: return null
        return document.adventures[key]
    }

    private fun isActiveBook(book: AdventureState): Boolean {
        val document = _project.value ?: return false
        val activeKey = document.activeAdventureKey ?: return false
        val active = document.adventures[activeKey] ?: return false
        return active.startedAt == book.startedAt && active.character.name == book.character.name
    }

    // ------------------------------------------------------------------
    // 生命周期 / 工具
    // ------------------------------------------------------------------

    private fun cancelAllJobs() {
        saveJob?.cancel()
        bibleJob?.cancel()
        cancelGameJobs()
    }

    private fun cancelGameJobs() {
        storyJob?.cancel()
        openingJob?.cancel()
        actionJob?.cancel()
        goalCheckJob?.cancel()
        endingJob?.cancel()
        epilogueJob?.cancel()
        typingJob?.cancel()
        polishJob?.cancel()
        storyJob = null
        openingJob = null
        actionJob = null
        goalCheckJob = null
        endingJob = null
        epilogueJob = null
        typingJob = null
        polishJob = null
        actionRunning = false
        endingRequestId++
        epilogueRequestId++
        openingKey = null
        loadedVerifyKey = null
        endingAutoKey = null
    }

    private fun resetGameUi() {
        goalCheckWarned = false
        _gameState.value = GameUiState()
    }

    private fun resetTransientStates() {
        _homeState.value = HomeUiState()
        _bibleState.value = BibleUiState()
        resetGameUi()
        openingKey = null
        loadedVerifyKey = null
        endingAutoKey = null
        endingRequestId++
        epilogueRequestId++
        epilogueReturnScreen = Screen.GAME
        bibleReturnScreen = Screen.HOME
    }

    private suspend fun refreshProjects() {
        val projects = withContext(Dispatchers.IO) {
            try {
                projectStore.list()
            } catch (_: Exception) {
                emptyList()
            }
        }
        _savedProjects.value = projects
    }

    private fun requireConfig(): AiConfig =
        settingsStore.read() ?: throw AiConfigurationException(NOT_CONFIGURED_MESSAGE)

    private fun readTextFromUri(uri: Uri): String {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        stream.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > ProjectStore.MAX_PROJECT_BYTES) {
                    throw IOException("JSON 文件过大（上限 4 MB）")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 600L
        private const val TYPING_INTERVAL_MS = 18L
        private const val TYPING_START_DELAY_MS = 120L
        private const val GOAL_CHECK_TIMEOUT_MS = 20_000L
        private const val ENDING_TIMEOUT_MS = 90_000L
        private const val EPILOGUE_TIMEOUT_MS = 120_000L
        private const val POLISH_TIMEOUT_MS = 30_000L
        private const val MAX_CUSTOM_CHARS = 200
    }
}
