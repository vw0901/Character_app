package com.ifitwereyou.character.data

import com.ifitwereyou.character.game.DEFAULT_TIMELINE
import com.ifitwereyou.character.game.DEFAULT_TOTAL_DAYS
import com.ifitwereyou.character.game.MAX_TOTAL_DAYS
import com.ifitwereyou.character.game.MIN_DAYS_PER_ACTION
import com.ifitwereyou.character.game.MIN_TOTAL_DAYS
import com.ifitwereyou.character.game.clamp
import com.ifitwereyou.character.game.clampDaysPerAction
import com.ifitwereyou.character.game.openingStats
import com.ifitwereyou.character.game.reconcilePlayingOutcome
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

enum class ProjectPhase { SEARCH, BIBLE, GAME }

private val SAFE_ID_REGEX = Regex("^[\\p{L}\\p{N}_-]{1,64}$")
private val SLUG_REGEX = Regex("[^\\p{L}\\p{N}_-]+")

fun nowIso(): String = Instant.now().toString()

fun makeProjectId(name: String): String {
    val slug = name.trim()
        .replace(SLUG_REGEX, "-")
        .trim('-')
        .take(40)
    return slug.ifEmpty { "novel-${System.currentTimeMillis().toString(36)}" }
}

private fun baseProject(novelName: String): ProjectDocument {
    val timestamp = nowIso()
    return ProjectDocument(
        format = PROJECT_FORMAT,
        formatVersion = PROJECT_FORMAT_VERSION,
        appVersion = APP_VERSION,
        id = makeProjectId(novelName.ifBlank { "未命名" }),
        novelName = novelName.ifBlank { "未命名" },
        createdAt = timestamp,
        updatedAt = timestamp,
        search = null,
        bible = null,
        storyOptions = emptyMap(),
        adventures = emptyMap(),
        activeAdventureKey = null,
    )
}

fun createProjectFromSearch(search: SearchData): ProjectDocument =
    baseProject(search.novelName).copy(search = search)

fun createProjectFromBible(bible: SettingBible): ProjectDocument =
    baseProject(bible.novelName).copy(bible = bible)

fun phaseForProject(project: ProjectDocument): ProjectPhase {
    val hasBook = project.activeAdventureKey != null || project.adventures.isNotEmpty()
    if (project.bible != null && hasBook) return ProjectPhase.GAME
    if (project.bible != null || project.search != null) return ProjectPhase.BIBLE
    return ProjectPhase.SEARCH
}

fun projectFileName(document: ProjectDocument): String {
    val name = document.novelName.replace(Regex("[\\\\/:*?\"<>|]+"), "-").take(40).ifBlank { "项目" }
    val stamp = document.updatedAt.take(10).ifBlank { LocalDate.now().toString() }
    return "$name-$stamp.json"
}

// ---------------------------------------------------------------------------
// 编码：导出与提示词共用，字段名与 TS 版的 JSON.stringify 保持一致。
// ---------------------------------------------------------------------------

object ProjectCodec {

    fun encodeProject(document: ProjectDocument, pretty: Boolean = true): String {
        val json = projectToJson(document)
        return if (pretty) json.toString(2) else json.toString()
    }

    fun projectToJson(document: ProjectDocument): JSONObject {
        val json = JSONObject()
        json.put("format", PROJECT_FORMAT)
        json.put("formatVersion", PROJECT_FORMAT_VERSION)
        json.put("appVersion", document.appVersion)
        json.put("id", document.id)
        json.put("novelName", document.novelName)
        json.put("createdAt", document.createdAt)
        json.put("updatedAt", document.updatedAt)
        putNullable(json, "search", document.search?.let { searchToJson(it) })
        putNullable(json, "bible", document.bible?.let { bibleToJson(it) })
        json.put("storyOptions", JSONObject().apply {
            document.storyOptions.forEach { (key, options) ->
                put(key, JSONArray().apply { options.forEach { put(storyOptionToJson(it)) } })
            }
        })
        val adventures = JSONObject()
        document.adventures.forEach { (key, adventure) -> adventures.put(key, adventureToJson(adventure)) }
        json.put("adventures", adventures)
        putNullable(json, "activeAdventureKey", document.activeAdventureKey)
        return json
    }

    fun searchToJson(search: SearchData): JSONObject = JSONObject().apply {
        put("novelName", search.novelName)
        put("summary", search.summary)
        put("results", JSONArray().apply {
            search.results.forEach { result ->
                put(JSONObject().apply {
                    put("title", result.title)
                    put("url", result.url)
                    put("snippet", result.snippet)
                    put("siteName", result.siteName)
                })
            }
        })
    }

    fun bibleToJson(bible: SettingBible): JSONObject = JSONObject().apply {
        put("novelName", bible.novelName)
        put("author", bible.author)
        put("status", bible.status)
        put("genre", bible.genre)
        put("synopsis", bible.synopsis)
        put("tone", bible.tone)
        put("themes", JSONArray(bible.themes))
        put("worldview", JSONObject().apply {
            put("summary", bible.worldview.summary)
            put("geography", bible.worldview.geography)
            put("history", bible.worldview.history)
            put("rules", bible.worldview.rules)
        })
        put("powerSystem", JSONObject().apply {
            put("name", bible.powerSystem.name)
            put("levels", JSONArray(bible.powerSystem.levels))
            put("description", bible.powerSystem.description)
            put("limitations", bible.powerSystem.limitations)
        })
        put("factions", JSONArray().apply {
            bible.factions.forEach { faction ->
                put(JSONObject().apply {
                    put("name", faction.name)
                    put("description", faction.description)
                    put("keyMembers", JSONArray(faction.keyMembers))
                    put("goal", faction.goal)
                })
            }
        })
        put("characters", JSONArray().apply {
            bible.characters.forEach { character ->
                put(JSONObject().apply {
                    put("name", character.name)
                    put("role", character.role)
                    put("abilities", character.abilities)
                    put("personality", character.personality)
                    put("relationships", character.relationships)
                })
            }
        })
        put("timeline", JSONArray().apply {
            bible.timeline.forEach { event ->
                put(JSONObject().apply {
                    put("event", event.event)
                    put("description", event.description)
                })
            }
        })
        put("mysteries", JSONArray().apply {
            bible.mysteries.forEach { mystery ->
                put(JSONObject().apply {
                    put("question", mystery.question)
                    put("clues", mystery.clues)
                    put("theories", mystery.theories)
                })
            }
        })
        put("locations", JSONArray().apply {
            bible.locations.forEach { location ->
                put(JSONObject().apply {
                    put("name", location.name)
                    put("description", location.description)
                    put("significance", location.significance)
                })
            }
        })
        put("keyItems", JSONArray().apply {
            bible.keyItems.forEach { item ->
                put(JSONObject().apply {
                    put("name", item.name)
                    put("description", item.description)
                    put("significance", item.significance)
                })
            }
        })
        put("iconicScenes", JSONArray().apply {
            bible.iconicScenes.forEach { scene ->
                put(JSONObject().apply {
                    put("title", scene.title)
                    put("description", scene.description)
                    put("impact", scene.impact)
                })
            }
        })
        put("quotes", JSONArray().apply {
            bible.quotes.forEach { quote ->
                put(JSONObject().apply {
                    put("text", quote.text)
                    put("speaker", quote.speaker)
                    put("context", quote.context)
                })
            }
        })
    }

    fun storyOptionToJson(story: StoryOption): JSONObject = JSONObject().apply {
        put("id", story.id)
        put("title", story.title)
        put("summary", story.summary)
        put("opening", story.opening)
        put("estimatedTurns", story.estimatedTurns)
        put("totalDays", story.totalDays)
    }

    fun statsToJson(stats: StatValues): JSONObject = JSONObject().apply {
        put("health", stats.health)
    }

    fun characterToJson(character: CharacterChoice): JSONObject = JSONObject().apply {
        put("mode", character.mode.wire)
        put("name", character.name)
        put("background", character.background)
        character.abilities?.let { put("abilities", it) }
        character.personality?.let { put("personality", it) }
    }

    fun sceneToJson(scene: Scene): JSONObject = JSONObject().apply {
        put("id", scene.id)
        put("location", scene.location)
        put("description", scene.description)
        put("choices", JSONArray().apply {
            scene.choices.forEach { choice ->
                put(JSONObject().apply {
                    put("id", choice.id)
                    put("text", choice.text)
                    if (choice.hint.isNotBlank()) put("hint", choice.hint)
                })
            }
        })
    }

    fun gameStateToJson(gameState: GameState): JSONObject = JSONObject().apply {
        put("title", gameState.title)
        put("intro", gameState.intro)
        put("playerCharacter", JSONObject().apply {
            put("name", gameState.playerCharacter.name)
            put("background", gameState.playerCharacter.background)
            put("initialStats", statsToJson(gameState.playerCharacter.initialStats))
        })
        put("currentScene", sceneToJson(gameState.currentScene))
    }

    fun historyToJson(history: List<HistoryEntry>): JSONArray = JSONArray().apply {
        history.forEach { entry ->
            put(JSONObject().apply {
                put("type", entry.type.wire)
                put("text", entry.text)
            })
        }
    }

    fun goalToJson(goal: StoryGoal): JSONObject = JSONObject().apply {
        put("title", goal.title)
        put("description", goal.description)
    }

    fun timelineToJson(timeline: StoryTimeline): JSONObject = JSONObject().apply {
        put("label", timeline.label)
        put("totalDays", timeline.totalDays)
        put("currentDay", timeline.currentDay)
    }

    fun snapshotToJson(snapshot: AdventureSnapshot): JSONObject = JSONObject().apply {
        put("gameState", gameStateToJson(snapshot.gameState))
        put("stats", statsToJson(snapshot.stats))
        put("history", historyToJson(snapshot.history))
        put("isEnding", snapshot.isEnding)
        put("endingText", snapshot.endingText)
        put("turn", snapshot.turn)
        put("timeline", timelineToJson(snapshot.timeline))
        put("outcome", snapshot.outcome.wire)
        put("outcomeReason", snapshot.outcomeReason)
    }

    fun adventureToJson(adventure: AdventureState): JSONObject = JSONObject().apply {
        put("character", characterToJson(adventure.character))
        putNullable(this, "story", adventure.story?.let { storyOptionToJson(it) })
        putNullable(this, "gameState", adventure.gameState?.let { gameStateToJson(it) })
        put("stats", statsToJson(adventure.stats))
        put("history", historyToJson(adventure.history))
        put("isEnding", adventure.isEnding)
        put("endingText", adventure.endingText)
        put("turn", adventure.turn)
        put("snapshots", JSONArray().apply { adventure.snapshots.forEach { put(snapshotToJson(it)) } })
        put("startedAt", adventure.startedAt)
        put("goal", goalToJson(adventure.goal))
        put("timeline", timelineToJson(adventure.timeline))
        put("outcome", adventure.outcome.wire)
        put("outcomeReason", adventure.outcomeReason)
        put("endingSummary", adventure.endingSummary)
        put("epilogue", adventure.epilogue)
    }
}

// ---------------------------------------------------------------------------
// 解析：宽松解析模型输出，并把导入的 JSON 收敛成完整结构。
// ---------------------------------------------------------------------------

fun normalizeSearchData(value: Any?, fallbackName: String = ""): SearchData? {
    val record = asRecord(value) ?: return null
    val results = (record.optArray("results") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val title = text(row.opt("title"))
            val snippet = text(row.opt("snippet"))
            if (title.isBlank() && snippet.isBlank()) return@mapNotNull null
            SearchResult(
                title = title,
                url = text(row.opt("url")),
                snippet = snippet,
                siteName = text(row.opt("siteName")).ifBlank { text(row.opt("site_name")) },
            )
        }
    }
    val novelName = text(record.opt("novelName"), fallbackName)
    if (novelName.isBlank() && results.isEmpty()) return null
    return SearchData(novelName = novelName, summary = text(record.opt("summary")), results = results)
}

fun normalizeBible(value: Any?, fallbackName: String = ""): SettingBible? {
    val record = asRecord(value) ?: return null
    val worldview = asRecord(record.opt("worldview")) ?: JSONObject()
    val powerSystem = asRecord(record.opt("powerSystem"))
        ?: asRecord(record.opt("power_system"))
        ?: JSONObject()

    val factions = (record.optArray("factions") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val name = text(row.opt("name"))
            if (name.isBlank()) return@mapNotNull null
            Faction(
                name = name,
                description = text(row.opt("description")),
                keyMembers = stringList(row.opt("keyMembers") ?: row.opt("key_members")),
                goal = text(row.opt("goal")),
            )
        }
    }
    val characters = (record.optArray("characters") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val name = text(row.opt("name"))
            if (name.isBlank()) return@mapNotNull null
            BibleCharacter(
                name = name,
                role = text(row.opt("role")),
                abilities = text(row.opt("abilities")),
                personality = text(row.opt("personality")),
                relationships = text(row.opt("relationships")),
            )
        }
    }
    val timeline = (record.optArray("timeline") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val event = text(row.opt("event"))
            if (event.isBlank()) return@mapNotNull null
            TimelineEvent(event = event, description = text(row.opt("description")))
        }
    }
    val mysteries = (record.optArray("mysteries") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val question = text(row.opt("question"))
            if (question.isBlank()) return@mapNotNull null
            Mystery(
                question = question,
                clues = text(row.opt("clues")),
                theories = text(row.opt("theories")),
            )
        }
    }
    val locations = (record.optArray("locations") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val name = text(row.opt("name"))
            if (name.isBlank()) return@mapNotNull null
            KeyLocation(
                name = name,
                description = text(row.opt("description")),
                significance = text(row.opt("significance")),
            )
        }
    }
    val keyItems = (record.optArray("keyItems", "key_items") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val name = text(row.opt("name"))
            if (name.isBlank()) return@mapNotNull null
            KeyItem(
                name = name,
                description = text(row.opt("description")),
                significance = text(row.opt("significance")),
            )
        }
    }
    val iconicScenes = (record.optArray("iconicScenes", "iconic_scenes") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val title = text(row.opt("title")).ifBlank { text(row.opt("event")) }
            val description = text(row.opt("description"))
            if (title.isBlank() && description.isBlank()) return@mapNotNull null
            IconicScene(
                title = title.ifBlank { "名场面" },
                description = description,
                impact = text(row.opt("impact"), text(row.opt("meaning"))),
            )
        }
    }
    val quotes = (record.optArray("quotes", "classicQuotes", "classic_quotes") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val content = text(row.opt("text")).ifBlank { text(row.opt("quote")) }
            if (content.isBlank()) return@mapNotNull null
            ClassicQuote(
                text = content,
                speaker = text(row.opt("speaker")),
                context = text(row.opt("context")),
            )
        }
    }

    val bible = SettingBible(
        novelName = text(record.opt("novelName"), fallbackName),
        author = text(record.opt("author")),
        status = text(record.opt("status")),
        genre = text(record.opt("genre")),
        synopsis = text(record.opt("synopsis")),
        tone = text(record.opt("tone")),
        themes = stringList(record.opt("themes")),
        worldview = Worldview(
            summary = text(worldview.opt("summary")),
            geography = text(worldview.opt("geography")),
            history = text(worldview.opt("history")),
            rules = text(worldview.opt("rules")),
        ),
        powerSystem = PowerSystem(
            name = text(powerSystem.opt("name")),
            levels = stringList(powerSystem.opt("levels")),
            description = text(powerSystem.opt("description")),
            limitations = text(powerSystem.opt("limitations")),
        ),
        factions = factions,
        characters = characters,
        timeline = timeline,
        mysteries = mysteries,
        locations = locations,
        keyItems = keyItems,
        iconicScenes = iconicScenes,
        quotes = quotes,
    )

    val recognizable = bible.novelName.isNotBlank() ||
        factions.isNotEmpty() ||
        characters.isNotEmpty() ||
        timeline.isNotEmpty() ||
        mysteries.isNotEmpty() ||
        locations.isNotEmpty() ||
        keyItems.isNotEmpty() ||
        iconicScenes.isNotEmpty() ||
        quotes.isNotEmpty() ||
        bible.tone.isNotBlank() ||
        bible.worldview.summary.isNotBlank() ||
        bible.synopsis.isNotBlank()
    return bible.takeIf { recognizable }
}

private fun normalizeStoryOption(value: Any?, index: Int, keepId: Boolean = false): StoryOption? {
    val record = asRecord(value) ?: return null
    val title = text(record.opt("title") ?: record.opt("name"))
    val summary = text(record.opt("summary") ?: record.opt("description"))
    if (title.isBlank() || summary.isBlank()) return null
    val turns = numberValue(
        record.opt("estimatedTurns") ?: record.opt("estimated_turns") ?: record.opt("turns"),
        8.0,
    ).roundToInt()
    val days = numberValue(record.opt("totalDays") ?: record.opt("total_days") ?: record.opt("days"), 20.0)
        .roundToInt()
    val safeDays = clamp(days, MIN_TOTAL_DAYS, MAX_TOTAL_DAYS)
    val storedId = text(record.opt("id"))
    return StoryOption(
        id = if (keepId && SAFE_ID_REGEX.matches(storedId)) storedId else "story_${index + 1}",
        title = title,
        summary = summary,
        opening = text(record.opt("opening") ?: record.opt("hook")),
        estimatedTurns = clamp(turns, 5, 12),
        totalDays = safeDays,
    )
}

/** 解析 AI 给出的故事栏；id 由客户端重新编号，避免模型输出重复或非法键。 */
fun normalizeStoryOptions(value: Any?): List<StoryOption> {
    val array = when (value) {
        is JSONArray -> value
        is JSONObject -> value.optArray("stories", "storyOptions", "story_options")
        else -> null
    } ?: return emptyList()
    return (0 until array.length())
        .mapNotNull { index -> normalizeStoryOption(array.opt(index), index) }
        .distinctBy { it.title }
        .take(5)
}

private fun normalizeStats(value: Any?): StatValues {
    val record = asRecord(value) ?: JSONObject()
    val health = clamp(numberValue(record.opt("health"), 100.0).roundToInt(), 0, 100)
    return StatValues(health = health)
}

private fun normalizeStatsChange(record: JSONObject): StatChange {
    val changes = asRecord(record.opt("statsChange") ?: record.opt("stats_change")) ?: JSONObject()
    return StatChange(health = numberValue(changes.opt("health")).roundToInt())
}

private fun normalizeCharacter(value: Any?): CharacterChoice? {
    val record = asRecord(value) ?: return null
    val name = text(record.opt("name"))
    if (name.isBlank()) return null
    return CharacterChoice(
        mode = CharacterMode.fromWire(record.opt("mode")?.toString()),
        name = name,
        background = text(record.opt("background")),
        abilities = text(record.opt("abilities")).takeIf { it.isNotBlank() },
        personality = text(record.opt("personality")).takeIf { it.isNotBlank() },
    )
}

fun normalizeScene(value: Any?, fallbackPrefix: String = "scene"): Scene? {
    val record = asRecord(value) ?: return null
    val choices = (record.optArray("choices") ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val row = asRecord(array.opt(index)) ?: return@mapNotNull null
            val label = text(row.opt("text")).ifBlank { text(row.opt("label")) }
            if (label.isBlank()) return@mapNotNull null
            SceneChoice(
                id = text(row.opt("id")).ifBlank { "${fallbackPrefix}_choice_${index + 1}" },
                text = label,
                hint = text(row.opt("hint")),
            )
        }
    }
    val location = text(record.opt("location"))
    val description = text(record.opt("description"))
    if (location.isBlank() && description.isBlank() && choices.isEmpty()) return null
    return Scene(
        id = text(record.opt("id")).ifBlank { "${fallbackPrefix}_1" },
        location = location,
        description = description,
        choices = choices,
    )
}

fun normalizeGameState(value: Any?): GameState? {
    val record = asRecord(value) ?: return null
    val scene = normalizeScene(record.opt("currentScene") ?: record.opt("current_scene")) ?: return null
    val player = asRecord(record.opt("playerCharacter") ?: record.opt("player_character")) ?: JSONObject()
    return GameState(
        title = text(record.opt("title")),
        intro = text(record.opt("intro")),
        playerCharacter = PlayerCharacter(
            name = text(player.opt("name")),
            background = text(player.opt("background")),
            initialStats = normalizeStats(player.opt("initialStats") ?: player.opt("initial_stats")),
        ),
        currentScene = scene,
    )
}

fun normalizeStoryGoal(value: Any?): StoryGoal {
    val record = asRecord(value) ?: JSONObject()
    val title = text(record.opt("title"))
    val description = text(record.opt("description"))
    if (title.isBlank() && description.isBlank()) {
        return StoryGoal("完成属于你的旅程", "在这个世界走到故事的终点。")
    }
    return StoryGoal(title.ifBlank { "完成属于你的旅程" }, description)
}

private fun buildTimeline(value: Any?, minTotal: Int, maxTotal: Int, forceDay: Int? = null): StoryTimeline {
    val record = asRecord(value) ?: JSONObject()
    val rawTotal = numberValue(record.opt("totalDays") ?: record.opt("total_days"), 0.0).roundToInt()
    val totalDays = if (rawTotal > 0) clamp(rawTotal, minTotal, maxTotal) else DEFAULT_TOTAL_DAYS
    val rawCurrent = max(
        1,
        numberValue(record.opt("currentDay") ?: record.opt("current_day"), 1.0).roundToInt(),
    )
    return StoryTimeline(
        label = text(record.opt("label")).ifBlank { DEFAULT_TIMELINE.label },
        totalDays = totalDays,
        currentDay = forceDay ?: rawCurrent,
    )
}

fun normalizeStoryTimeline(value: Any?): StoryTimeline =
    buildTimeline(value, minTotal = 1, maxTotal = 365)

private fun normalizeOpeningTimeline(value: Any?): StoryTimeline =
    buildTimeline(value, minTotal = MIN_TOTAL_DAYS, maxTotal = MAX_TOTAL_DAYS, forceDay = 1)

private fun normalizeOpeningGoal(value: Any?): StoryGoal? {
    if (value is String) {
        val title = text(value)
        return title.takeIf { it.isNotBlank() }
            ?.let { StoryGoal(it, "在时间线耗尽前达成这件事。") }
    }
    val record = asRecord(value) ?: return null
    val title = text(record.opt("title"))
    if (title.isBlank()) return null
    return StoryGoal(
        title = title,
        description = text(record.opt("description")).ifBlank { "在时间线耗尽前达成这件事。" },
    )
}

fun normalizeGameOpening(value: Any?, story: StoryOption? = null): GameOpening? {
    val record = asRecord(value) ?: return null
    val gameState = normalizeGameState(value) ?: return null
    val goal = normalizeOpeningGoal(record.opt("finalGoal") ?: record.opt("final_goal")) ?: return null
    if (gameState.currentScene.choices.isEmpty()) return null
    val timeline = normalizeOpeningTimeline(record.opt("timeline")).let { parsed ->
        if (story == null) parsed
        else parsed.copy(
            totalDays = clamp(story.totalDays, MIN_TOTAL_DAYS, MAX_TOTAL_DAYS),
            currentDay = 1,
        )
    }
    return GameOpening(
        gameState = gameState.copy(
            playerCharacter = gameState.playerCharacter.copy(
                initialStats = openingStats(gameState.playerCharacter.initialStats),
            ),
        ),
        goal = goal,
        timeline = timeline,
    )
}

fun normalizeSceneResult(value: Any?): SceneResult? {
    val record = asRecord(value) ?: return null
    val scene = normalizeScene(record.opt("currentScene") ?: record.opt("current_scene"), "scene")
    val narration = text(record.opt("narration"))
    val goalAchieved = record.opt("goalAchieved") == true || record.opt("goal_achieved") == true
    val isEnding = record.opt("isEnding") == true || record.opt("is_ending") == true || goalAchieved
    val endingText = text(record.opt("endingText") ?: record.opt("ending_text"))
    val endingType = EndingType.fromWire((record.opt("endingType") ?: record.opt("ending_type"))?.toString())
    val daysPassed = clampDaysPerAction(
        numberValue(
            record.opt("daysPassed") ?: record.opt("days_passed"),
            MIN_DAYS_PER_ACTION.toDouble(),
        ),
    )

    if (isEnding) {
        if (narration.isBlank() && endingText.isBlank()) return null
        return SceneResult(
            narration = narration,
            currentScene = scene ?: Scene(id = "ending"),
            statsChange = normalizeStatsChange(record),
            isEnding = true,
            endingType = endingType,
            daysPassed = daysPassed,
            goalAchieved = goalAchieved,
            endingText = endingText,
        )
    }

    if (scene == null || scene.choices.isEmpty()) return null
    return SceneResult(
        narration = narration,
        currentScene = scene,
        statsChange = normalizeStatsChange(record),
        isEnding = false,
        endingType = endingType,
        daysPassed = daysPassed,
        goalAchieved = goalAchieved,
        endingText = endingText,
    )
}

fun normalizeGoalCheck(value: Any?): GoalCheckVerdict? {
    val record = asRecord(value) ?: return null
    if (record.opt("ok") == false) return null
    val achieved = record.opt("achieved") as? Boolean ?: return null
    return GoalCheckVerdict(
        achieved = achieved,
        confidence = GoalConfidence.fromWire(record.opt("confidence")?.toString()),
        reason = text(record.opt("reason")),
    )
}

private fun normalizeHistory(value: Any?): List<HistoryEntry> {
    val array = value as? JSONArray ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val row = asRecord(array.opt(index)) ?: return@mapNotNull null
        val content = text(row.opt("text"))
        if (content.isBlank()) return@mapNotNull null
        HistoryEntry(type = HistoryType.fromWire(row.opt("type")?.toString()), text = content)
    }
}

private fun normalizeSnapshots(value: Any?): List<AdventureSnapshot> {
    val array = value as? JSONArray ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val record = asRecord(array.opt(index)) ?: return@mapNotNull null
        val gameState = normalizeGameState(record.opt("gameState") ?: record.opt("game_state"))
            ?: return@mapNotNull null
        val isEnding = record.opt("isEnding") == true
        AdventureSnapshot(
            gameState = gameState,
            stats = normalizeStats(record.opt("stats")),
            history = normalizeHistory(record.opt("history")),
            isEnding = isEnding,
            endingText = text(record.opt("endingText") ?: record.opt("ending_text")),
            turn = max(0, numberValue(record.opt("turn")).roundToInt()),
            timeline = normalizeStoryTimeline(record.opt("timeline")),
            outcome = normalizeOutcome(record.opt("outcome")?.toString(), isEnding),
            outcomeReason = text(record.opt("outcomeReason") ?: record.opt("outcome_reason")),
        )
    }.takeLast(MAX_UNDO_SNAPSHOTS)
}

private fun normalizeOutcome(value: String?, isEnding: Boolean): AdventureOutcome {
    if (value == AdventureOutcome.SUCCESS.wire || value == AdventureOutcome.FAILURE.wire) {
        return AdventureOutcome.fromWire(value)
    }
    return if (isEnding) AdventureOutcome.FAILURE else AdventureOutcome.PLAYING
}

fun normalizeAdventure(value: Any?): AdventureState? {
    val record = asRecord(value) ?: return null
    val character = normalizeCharacter(record.opt("character")) ?: return null
    val isEnding = record.opt("isEnding") == true

    val adventure = AdventureState(
        character = character,
        story = normalizeStoryOption(record.opt("story"), 0, keepId = true),
        gameState = normalizeGameState(record.opt("gameState") ?: record.opt("game_state")),
        stats = normalizeStats(record.opt("stats")),
        history = normalizeHistory(record.opt("history")),
        isEnding = isEnding,
        endingText = text(record.opt("endingText") ?: record.opt("ending_text")),
        turn = max(0, numberValue(record.opt("turn")).roundToInt()),
        snapshots = normalizeSnapshots(record.opt("snapshots")),
        startedAt = text(record.opt("startedAt") ?: record.opt("started_at")).ifBlank { nowIso() },
        goal = normalizeStoryGoal(record.opt("goal") ?: record.opt("finalGoal") ?: record.opt("final_goal")),
        timeline = normalizeStoryTimeline(record.opt("timeline")),
        outcome = normalizeOutcome(record.opt("outcome")?.toString(), isEnding),
        outcomeReason = text(record.opt("outcomeReason") ?: record.opt("outcome_reason")),
        endingSummary = text(record.opt("endingSummary") ?: record.opt("ending_summary")),
        epilogue = text(record.opt("epilogue")),
    )

    val (reconciledOutcome, reconciledReason) = reconcilePlayingOutcome(
        outcome = adventure.outcome,
        isEnding = adventure.isEnding,
        stats = adventure.stats,
        timeline = adventure.timeline,
    )
    if (reconciledOutcome == adventure.outcome) return adventure
    return adventure.copy(
        outcome = reconciledOutcome,
        outcomeReason = reconciledReason,
        isEnding = true,
    )
}

private fun normalizeStoryOptionMap(record: JSONObject): Map<String, List<StoryOption>> {
    val raw = asRecord(record.opt("storyOptions") ?: record.opt("story_options")) ?: return emptyMap()
    val result = LinkedHashMap<String, List<StoryOption>>()
    val keys = raw.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = raw.opt(key)
        val options = when (value) {
            is JSONArray -> (0 until value.length())
                .mapNotNull { index -> normalizeStoryOption(value.opt(index), index, keepId = true) }
            else -> normalizeStoryOptions(value)
        }
        if (options.isNotEmpty()) result[key] = options.distinctBy { it.id }
    }
    return result
}

private fun normalizeAdventures(record: JSONObject): Pair<Map<String, AdventureState>, String?> {
    val adventures = LinkedHashMap<String, AdventureState>()
    val raw = asRecord(record.opt("adventures"))
    if (raw != null) {
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            normalizeAdventure(raw.opt(key))?.let { adventures[key] = it }
        }
    }

    val legacy = normalizeAdventure(record.opt("adventure"))
    var legacyActiveKey: String? = null
    if (legacy != null) {
        val key = com.ifitwereyou.character.game.characterKey(legacy.character)
        if (!adventures.containsKey(key)) adventures[key] = legacy
        legacyActiveKey = key
    }
    return adventures to legacyActiveKey
}

fun normalizeProject(value: Any?): ProjectDocument? {
    val record = asRecord(value) ?: return null

    val search = normalizeSearchData(record.opt("search"))
    val bible = normalizeBible(record.opt("bible"))
    val storyOptions = normalizeStoryOptionMap(record)
    val (adventures, legacyActiveKey) = normalizeAdventures(record)

    if (search == null && bible == null && adventures.isEmpty()) {
        return null
    }

    val novelName = text(record.opt("novelName"))
        .ifBlank { search?.novelName.orEmpty() }
        .ifBlank { bible?.novelName.orEmpty() }
        .ifBlank { adventures.values.firstOrNull { it.gameState?.title?.isNotBlank() == true }?.gameState?.title.orEmpty() }
        .ifBlank { "未命名" }

    val base = baseProject(novelName)
    val storedId = text(record.opt("id"))
    val storedActive = text(record.opt("activeAdventureKey"))
    return base.copy(
        id = storedId.takeIf { SAFE_ID_REGEX.matches(it) } ?: base.id,
        createdAt = text(record.opt("createdAt")).ifBlank { base.createdAt },
        updatedAt = text(record.opt("updatedAt")).ifBlank { base.updatedAt },
        appVersion = text(record.opt("appVersion")).ifBlank { APP_VERSION },
        search = search,
        bible = bible,
        storyOptions = storyOptions,
        adventures = adventures,
        activeAdventureKey = storedActive.takeIf { adventures.containsKey(it) } ?: legacyActiveKey,
    )
}

fun parseImportedJson(raw: String): ImportedProject {
    val parsed = parseJsonLoose(raw.trimStart('\uFEFF')) ?: throw IllegalArgumentException("文件内容不是有效的 JSON")
    val record = asRecord(parsed) ?: throw IllegalArgumentException("JSON 顶层必须是一个对象")

    val looksLikeProject = record.opt("format") == PROJECT_FORMAT ||
        record.has("search") ||
        record.has("bible") ||
        record.has("adventure") ||
        record.has("adventures")

    if (looksLikeProject) {
        normalizeProject(record)?.let { return ImportedProject(it, ImportKind.PROJECT) }
    }

    val bibleFields = listOf(
        "worldview", "powerSystem", "power_system", "factions",
        "characters", "timeline", "mysteries", "locations",
        "keyItems", "key_items", "iconicScenes", "iconic_scenes", "quotes",
    )
    val looksLikeBible = bibleFields.any { record.has(it) }
    val looksLikeSearch = record.has("results") || (!looksLikeBible && record.has("summary"))

    if (looksLikeBible) {
        normalizeBible(record)?.let { return ImportedProject(createProjectFromBible(it), ImportKind.BIBLE) }
    }
    if (looksLikeSearch) {
        normalizeSearchData(record)?.let { return ImportedProject(createProjectFromSearch(it), ImportKind.SEARCH) }
    }

    normalizeBible(record)?.let { return ImportedProject(createProjectFromBible(it), ImportKind.BIBLE) }
    normalizeSearchData(record)?.let { return ImportedProject(createProjectFromSearch(it), ImportKind.SEARCH) }
    throw IllegalArgumentException("无法识别的 JSON：既不是项目存档，也不是设定圣经或检索结果")
}
