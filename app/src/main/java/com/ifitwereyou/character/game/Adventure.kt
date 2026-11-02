package com.ifitwereyou.character.game

import com.ifitwereyou.character.data.AdventureSnapshot
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CharacterMode
import com.ifitwereyou.character.data.GameState
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryOption
import com.ifitwereyou.character.data.StoryTimeline
import java.time.Instant

/** 故事书的键：同一本书在存档里只对应一个角色。 */
fun characterKey(character: CharacterChoice): String {
    val mode = if (character.mode == CharacterMode.CUSTOM) "custom" else "bible"
    return "$mode:${character.name.trim()}"
}

/** 同一角色下，每条故事线各自一份存档。 */
fun storyKey(character: CharacterChoice, storyId: String): String =
    "${characterKey(character)}#${storyId.trim()}"

/** 从冒险书键反推角色键；旧存档没有 `#` 时返回自身。 */
fun characterKeyOfBook(bookKey: String): String = bookKey.substringBefore('#')

val PENDING_GOAL = StoryGoal(
    title = "等待命运揭晓",
    description = "故事开始时会确定这个角色的最终目标。",
)

val DEFAULT_TIMELINE = StoryTimeline(label = "主线", totalDays = DEFAULT_TOTAL_DAYS, currentDay = 1)

fun createAdventure(
    character: CharacterChoice,
    story: StoryOption? = null,
): AdventureState = AdventureState(
    character = character,
    story = story,
    gameState = null,
    stats = DEFAULT_STATS,
    history = emptyList(),
    isEnding = false,
    endingText = "",
    turn = 0,
    snapshots = emptyList(),
    startedAt = Instant.now().toString(),
    goal = PENDING_GOAL.copy(),
    timeline = DEFAULT_TIMELINE.copy(totalDays = story?.totalDays ?: DEFAULT_TOTAL_DAYS),
    outcome = com.ifitwereyou.character.data.AdventureOutcome.PLAYING,
    outcomeReason = "",
    endingSummary = "",
    epilogue = "",
)

fun snapshotOf(adventure: AdventureState, gameState: GameState): AdventureSnapshot =
    AdventureSnapshot(
        gameState = gameState,
        stats = adventure.stats,
        history = adventure.history,
        isEnding = adventure.isEnding,
        endingText = adventure.endingText,
        turn = adventure.turn,
        timeline = adventure.timeline,
        outcome = adventure.outcome,
        outcomeReason = adventure.outcomeReason,
    )

/** 结果叙述 + 新场景描述，玩家才能读到「你现在在哪」。 */
fun joinNarration(narration: String, sceneDescription: String): String =
    listOf(narration.trim(), sceneDescription.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
