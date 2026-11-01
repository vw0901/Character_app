package com.ifitwereyou.character.data

const val PROJECT_FORMAT = "if-it-were-you/project"
const val PROJECT_FORMAT_VERSION = 2
const val APP_VERSION = "0.4.0"
const val MAX_UNDO_SNAPSHOTS = 20

enum class CharacterMode(val wire: String) {
    EXISTING("existing"),
    CUSTOM("custom");

    companion object {
        fun fromWire(value: String?): CharacterMode = if (value == CUSTOM.wire) CUSTOM else EXISTING
    }
}

data class CharacterChoice(
    val mode: CharacterMode = CharacterMode.EXISTING,
    val name: String = "",
    val background: String = "",
    val abilities: String? = null,
    val personality: String? = null,
)

data class SearchResult(
    val title: String = "",
    val url: String = "",
    val snippet: String = "",
    val siteName: String = "",
)

data class SearchData(
    val novelName: String = "",
    val summary: String = "",
    val results: List<SearchResult> = emptyList(),
)

data class Worldview(
    val summary: String = "",
    val geography: String = "",
    val history: String = "",
    val rules: String = "",
)

data class PowerSystem(
    val name: String = "",
    val levels: List<String> = emptyList(),
    val description: String = "",
    val limitations: String = "",
)

data class Faction(
    val name: String = "",
    val description: String = "",
    val keyMembers: List<String> = emptyList(),
    val goal: String = "",
)

data class BibleCharacter(
    val name: String = "",
    val role: String = "",
    val abilities: String = "",
    val personality: String = "",
    val relationships: String = "",
)

data class TimelineEvent(
    val event: String = "",
    val description: String = "",
)

data class Mystery(
    val question: String = "",
    val clues: String = "",
    val theories: String = "",
)

data class SettingBible(
    val novelName: String = "",
    val author: String = "",
    val status: String = "",
    val genre: String = "",
    val synopsis: String = "",
    val worldview: Worldview = Worldview(),
    val powerSystem: PowerSystem = PowerSystem(),
    val factions: List<Faction> = emptyList(),
    val characters: List<BibleCharacter> = emptyList(),
    val timeline: List<TimelineEvent> = emptyList(),
    val mysteries: List<Mystery> = emptyList(),
)

data class CheckIssue(
    val category: String = "",
    val severity: String = "",
    val title: String = "",
    val description: String = "",
    val fix: String = "",
)

data class CheckResult(
    val overallScore: Int = 0,
    val summary: String = "",
    val issues: List<CheckIssue> = emptyList(),
    val strengths: List<String> = emptyList(),
)

data class RepairFix(
    val issue: String = "",
    val action: String = "",
    val field: String = "",
)

data class RepairResult(
    val summary: String = "",
    val appliedFixes: List<RepairFix> = emptyList(),
    val bible: SettingBible = SettingBible(),
)

data class SceneChoice(
    val id: String = "",
    val text: String = "",
    val hint: String = "",
)

data class Scene(
    val id: String = "",
    val location: String = "",
    val description: String = "",
    val choices: List<SceneChoice> = emptyList(),
)

data class PlayerCharacter(
    val name: String = "",
    val background: String = "",
    val initialStats: StatValues = StatValues(),
)

data class GameState(
    val title: String = "",
    val intro: String = "",
    val playerCharacter: PlayerCharacter = PlayerCharacter(),
    val currentScene: Scene = Scene(),
)

data class StatValues(
    val health: Int = 100,
    val power: Int = 50,
    val reputation: Int = 50,
)

data class StatChange(
    val health: Int = 0,
    val power: Int = 0,
    val reputation: Int = 0,
)

enum class HistoryType(val wire: String) {
    NARRATION("narration"),
    CHOICE("choice");

    companion object {
        fun fromWire(value: String?): HistoryType = if (value == CHOICE.wire) CHOICE else NARRATION
    }
}

data class HistoryEntry(
    val type: HistoryType,
    val text: String,
)

data class StoryGoal(
    val title: String = "",
    val description: String = "",
)

data class StoryTimeline(
    val label: String = "主线",
    val totalDays: Int = 20,
    val currentDay: Int = 1,
)

enum class AdventureOutcome(val wire: String) {
    PLAYING("playing"),
    SUCCESS("success"),
    FAILURE("failure");

    companion object {
        fun fromWire(value: String?): AdventureOutcome =
            when (value) {
                SUCCESS.wire -> SUCCESS
                FAILURE.wire -> FAILURE
                else -> PLAYING
            }
    }
}

enum class EndingType(val wire: String) {
    SUCCESS("success"),
    FAILURE("failure");

    companion object {
        fun fromWire(value: String?): EndingType? =
            when (value) {
                SUCCESS.wire -> SUCCESS
                FAILURE.wire -> FAILURE
                else -> null
            }
    }
}

data class AdventureSnapshot(
    val gameState: GameState,
    val stats: StatValues,
    val history: List<HistoryEntry>,
    val isEnding: Boolean,
    val endingText: String,
    val turn: Int,
    val timeline: StoryTimeline,
    val outcome: AdventureOutcome,
    val outcomeReason: String,
)

data class AdventureState(
    val character: CharacterChoice,
    val gameState: GameState? = null,
    val stats: StatValues = StatValues(),
    val history: List<HistoryEntry> = emptyList(),
    val isEnding: Boolean = false,
    val endingText: String = "",
    val turn: Int = 0,
    val snapshots: List<AdventureSnapshot> = emptyList(),
    val startedAt: String = "",
    val goal: StoryGoal = StoryGoal(),
    val timeline: StoryTimeline = StoryTimeline(),
    val outcome: AdventureOutcome = AdventureOutcome.PLAYING,
    val outcomeReason: String = "",
    val epilogue: String = "",
)

data class SceneResult(
    val narration: String,
    val currentScene: Scene,
    val statsChange: StatChange,
    val isEnding: Boolean,
    val endingType: EndingType?,
    val daysPassed: Int,
    val goalAchieved: Boolean,
    val endingText: String,
)

data class GameOpening(
    val gameState: GameState,
    val goal: StoryGoal,
    val timeline: StoryTimeline,
)

enum class GoalConfidence(val wire: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromWire(value: String?): GoalConfidence =
            when (value) {
                HIGH.wire -> HIGH
                MEDIUM.wire -> MEDIUM
                else -> LOW
            }
    }
}

data class GoalCheckVerdict(
    val achieved: Boolean,
    val confidence: GoalConfidence,
    val reason: String,
)

data class ProjectDocument(
    val format: String = PROJECT_FORMAT,
    val formatVersion: Int = PROJECT_FORMAT_VERSION,
    val appVersion: String = APP_VERSION,
    val id: String = "",
    val novelName: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val search: SearchData? = null,
    val bible: SettingBible? = null,
    val check: CheckResult? = null,
    val repair: RepairResult? = null,
    val adventures: Map<String, AdventureState> = emptyMap(),
    val activeAdventureKey: String? = null,
)

data class ProjectSummary(
    val id: String,
    val novelName: String,
    val updatedAt: String,
    val appVersion: String,
    val hasSearch: Boolean,
    val hasBible: Boolean,
    val hasCheck: Boolean,
    val hasRepair: Boolean,
    val hasAdventure: Boolean,
)

enum class ImportKind(val label: String) {
    PROJECT("项目存档"),
    BIBLE("设定圣经"),
    SEARCH("检索结果"),
}

data class ImportedProject(
    val project: ProjectDocument,
    val kind: ImportKind,
)

enum class SaveState(val label: String) {
    IDLE("未保存"),
    SAVING("保存中…"),
    SAVED("已保存到本机"),
    ERROR("保存失败"),
}
