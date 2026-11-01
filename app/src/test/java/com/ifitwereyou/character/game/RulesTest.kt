package com.ifitwereyou.character.game

import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.EndingType
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.SceneResult
import com.ifitwereyou.character.data.StatChange
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun adventure(
        goal: StoryGoal = StoryGoal("找到妹妹", "在血月前带她离开"),
        timeline: StoryTimeline = StoryTimeline("血月降临前", 20, 18),
    ) = AdventureState(
        character = com.ifitwereyou.character.data.CharacterChoice(name = "林澈"),
        goal = goal,
        timeline = timeline,
    )

    private fun result(
        goalAchieved: Boolean = false,
        isEnding: Boolean = false,
        endingType: EndingType? = null,
        daysPassed: Int = 1,
    ) = SceneResult(
        narration = "叙述",
        currentScene = Scene(id = "scene_2"),
        statsChange = StatChange(),
        isEnding = isEnding,
        endingType = endingType,
        daysPassed = daysPassed,
        goalAchieved = goalAchieved,
        endingText = "",
    )

    @Test
    fun openingStatsNeverStartsDead() {
        assertEquals(20, openingStats(StatValues(health = 0)).health)
        assertEquals(60, openingStats(StatValues(health = 60)).health)
        assertEquals(100, openingStats(StatValues(health = 500)).health)
    }

    @Test
    fun nonFinalStatChangesAreCapped() {
        val base = StatValues(health = 100, power = 50, reputation = 50)
        val next = applyStatChange(base, StatChange(health = -80, power = 80, reputation = -80))
        assertEquals(60, next.health)
        assertEquals(80, next.power)
        assertEquals(20, next.reputation)
    }

    @Test
    fun finalStatChangesAreNotCappedButClamped() {
        val base = StatValues(health = 100, power = 50, reputation = 50)
        val next = applyStatChange(
            base,
            StatChange(health = -100, power = 90, reputation = 90),
            final = true,
        )
        assertEquals(0, next.health)
        assertEquals(100, next.power)
        assertEquals(100, next.reputation)
    }

    @Test
    fun daysPerActionIsClamped() {
        assertEquals(1, clampDaysPerAction(-5.0))
        assertEquals(1, clampDaysPerAction(0.0))
        assertEquals(2, clampDaysPerAction(1.6))
        assertEquals(3, clampDaysPerAction(9.0))
    }

    @Test
    fun goalAchievedWinsEvenIfHealthWouldDropToZero() {
        val current = adventure(timeline = StoryTimeline("血月", 20, 19))
        val resolution = resolveOutcome(
            current,
            StatValues(health = 0),
            result(goalAchieved = true, daysPassed = 2),
        )
        assertEquals(AdventureOutcome.SUCCESS, resolution.outcome)
        assertEquals(21, resolution.timeline.currentDay)
        assertTrue(resolution.reason.contains("找到妹妹"))
    }

    @Test
    fun deathFailsBeforeTimelineIsExhausted() {
        val current = adventure(timeline = StoryTimeline("血月", 20, 18))
        val resolution = resolveOutcome(current, StatValues(health = 0), result())
        assertEquals(AdventureOutcome.FAILURE, resolution.outcome)
        assertEquals(HEALTH_FAILURE_REASON, resolution.reason)
    }

    @Test
    fun timelineExhaustionFails() {
        val current = adventure(timeline = StoryTimeline("血月", 20, 20))
        val resolution = resolveOutcome(current, StatValues(health = 50), result(daysPassed = 1))
        assertEquals(AdventureOutcome.FAILURE, resolution.outcome)
        assertTrue(resolution.reason.contains("时间线耗尽"))
    }

    @Test
    fun explicitFailureEndingFails() {
        val current = adventure()
        val resolution = resolveOutcome(
            current,
            StatValues(health = 50),
            result(isEnding = true, endingType = EndingType.FAILURE),
        )
        assertEquals(AdventureOutcome.FAILURE, resolution.outcome)
        assertEquals(ENDING_FAILURE_REASON, resolution.reason)
    }

    @Test
    fun localGoalDetectionRequiresExplicitCompletion() {
        assertTrue(detectGoalAchievedFromText("你成功达成最终目标，带着妹妹离开了古桥。"))
        assertTrue(detectGoalAchievedFromText("大功告成。"))
        assertFalse(detectGoalAchievedFromText("你只拿到了一条线索。"))
        assertFalse(detectGoalAchievedFromText("你没能达成最终目标。"))
        assertFalse(detectGoalAchievedFromText("你差点就完成了最终目标。"))
    }

    @Test
    fun reconcileMarksBrokenPlayingSaveAsFailure() {
        val (outcome, reason) = reconcilePlayingOutcome(
            AdventureOutcome.PLAYING,
            isEnding = false,
            stats = StatValues(health = 0),
            timeline = StoryTimeline("血月", 20, 3),
        )
        assertEquals(AdventureOutcome.FAILURE, outcome)
        assertEquals(HEALTH_FAILURE_REASON, reason)
    }

    @Test
    fun reconcileLeavesCompletedStoriesAlone() {
        val (outcome, _) = reconcilePlayingOutcome(
            AdventureOutcome.SUCCESS,
            isEnding = true,
            stats = StatValues(health = 0),
            timeline = StoryTimeline("血月", 20, 3),
        )
        assertEquals(AdventureOutcome.SUCCESS, outcome)
    }
}
