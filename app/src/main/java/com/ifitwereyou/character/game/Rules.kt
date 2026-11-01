package com.ifitwereyou.character.game

import com.ifitwereyou.character.data.AdventureOutcome
import com.ifitwereyou.character.data.AdventureState
import com.ifitwereyou.character.data.EndingType
import com.ifitwereyou.character.data.SceneResult
import com.ifitwereyou.character.data.StatChange
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryTimeline
import kotlin.math.max
import kotlin.math.roundToInt

/** 所有属性都收敛到 0-100。 */
const val STAT_MIN = 0
const val STAT_MAX = 100

val DEFAULT_STATS = StatValues(health = 100, power = 50, reputation = 50)

/**
 * 单次行动对属性的影响上限。
 * 非结局回合封顶，避免「擦破皮掉 80 血」「一句好话回满血」这类不合理的加减；
 * 结局回合不截断，否则「当场阵亡」会出现一个还剩半管血的尸体。
 */
val STAT_CHANGE_LIMITS = mapOf(
    "health" to (-40 to 25),
    "power" to (-30 to 30),
    "reputation" to (-30 to 30),
)

/** 开局生命值下限：还没开始就先判死，等于生成了一个无法完成的局。 */
const val OPENING_HEALTH_MIN = 20

/** 一局故事的天数区间，与提示词、README 中的「15-30 天」保持一致。 */
const val MIN_TOTAL_DAYS = 15
const val MAX_TOTAL_DAYS = 30
const val DEFAULT_TOTAL_DAYS = 20

/** 一次行动消耗的天数区间。 */
const val MIN_DAYS_PER_ACTION = 1
const val MAX_DAYS_PER_ACTION = 3

const val HEALTH_FAILURE_REASON = "生命值归零，冒险失败"
const val ENDING_FAILURE_REASON = "故事以失败告终"

fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

fun clamp(value: Double, min: Double, max: Double): Double = value.coerceIn(min, max)

fun clampDaysPerAction(value: Double): Int {
    val days = if (value.isFinite()) value.roundToInt() else MIN_DAYS_PER_ACTION
    return clamp(days, MIN_DAYS_PER_ACTION, MAX_DAYS_PER_ACTION)
}

/** 把属性值收敛成 0-100 的整数；healthFloor 用来保证开局不是「已死」。 */
fun sanitizeStats(value: StatValues?, healthFloor: Int = STAT_MIN): StatValues {
    val input = value ?: DEFAULT_STATS
    return StatValues(
        health = clamp(input.health, healthFloor, STAT_MAX),
        power = clamp(input.power, STAT_MIN, STAT_MAX),
        reputation = clamp(input.reputation, STAT_MIN, STAT_MAX),
    )
}

/** 开局属性：生命值至少 OPENING_HEALTH_MIN，避免一开局就注定的失败局。 */
fun openingStats(value: StatValues?): StatValues = sanitizeStats(value, OPENING_HEALTH_MIN)

fun applyStatChange(
    base: StatValues,
    change: StatChange,
    final: Boolean = false,
): StatValues {
    fun step(key: String, raw: Int): Int {
        val (min, max) = STAT_CHANGE_LIMITS.getValue(key)
        return if (final) clamp(raw, -STAT_MAX, STAT_MAX) else clamp(raw, min, max)
    }
    return StatValues(
        health = clamp(base.health + step("health", change.health), STAT_MIN, STAT_MAX),
        power = clamp(base.power + step("power", change.power), STAT_MIN, STAT_MAX),
        reputation = clamp(base.reputation + step("reputation", change.reputation), STAT_MIN, STAT_MAX),
    )
}

fun isDead(stats: StatValues): Boolean = stats.health <= 0

fun isTimelineExhausted(timeline: StoryTimeline): Boolean = timeline.currentDay > timeline.totalDays

fun daysRemaining(timeline: StoryTimeline): Int = max(0, timeline.totalDays - timeline.currentDay)

fun timelineFailureReason(timeline: StoryTimeline): String =
    "时间线耗尽（第 ${timeline.currentDay} 天 / 共 ${timeline.totalDays} 天），未能达成最终目标"

data class OutcomeResolution(
    val outcome: AdventureOutcome,
    val reason: String,
    val timeline: StoryTimeline,
)

/**
 * 由客户端统一裁定结局，避免模型漏报或误报：
 * 达成目标 → 成功；生命归零 / 超过时间线 → 失败。
 * 先判成功再判死亡：同归于尽式的达成也算成功。
 */
fun resolveOutcome(
    current: AdventureState,
    nextStats: StatValues,
    result: SceneResult,
): OutcomeResolution {
    val timeline = current.timeline.copy(
        currentDay = current.timeline.currentDay + max(0, result.daysPassed),
    )

    if (result.goalAchieved || result.endingType == EndingType.SUCCESS) {
        return OutcomeResolution(
            outcome = AdventureOutcome.SUCCESS,
            reason = "达成最终目标：${current.goal.title}",
            timeline = timeline,
        )
    }
    if (isDead(nextStats)) {
        return OutcomeResolution(AdventureOutcome.FAILURE, HEALTH_FAILURE_REASON, timeline)
    }
    if (isTimelineExhausted(timeline)) {
        return OutcomeResolution(AdventureOutcome.FAILURE, timelineFailureReason(timeline), timeline)
    }
    if (result.isEnding) {
        return OutcomeResolution(AdventureOutcome.FAILURE, ENDING_FAILURE_REASON, timeline)
    }
    return OutcomeResolution(AdventureOutcome.PLAYING, "", timeline)
}

/**
 * 读档 / 回退后的状态对账：只修正「还标着进行中、但按规则已经结束」的状态，
 * 已经写入的结局保持不变。
 */
fun reconcilePlayingOutcome(
    outcome: AdventureOutcome,
    isEnding: Boolean,
    stats: StatValues,
    timeline: StoryTimeline,
): Pair<AdventureOutcome, String> {
    if (outcome != AdventureOutcome.PLAYING) return outcome to ""
    if (isDead(stats)) return AdventureOutcome.FAILURE to HEALTH_FAILURE_REASON
    if (isTimelineExhausted(timeline)) {
        return AdventureOutcome.FAILURE to timelineFailureReason(timeline)
    }
    if (isEnding) return AdventureOutcome.FAILURE to ENDING_FAILURE_REASON
    return AdventureOutcome.PLAYING to ""
}

/**
 * 判定接口不可用时的本地兜底。
 *
 * 只认叙述里明确写出「最终目标已达成」这类句子：像「找到线索」这种局部进展
 * 不能等同于「找到妹妹」，宁可漏判，也不要把进行中的故事误判成成功。
 */
private val GOAL_COMPLETION_PATTERNS = listOf(
    Regex("(?:最终目标|主线目标|终极目标)[^。！？!?\\n]{0,12}(?:达成|完成|实现|做到)"),
    Regex("(?:达成|完成|实现|做到)[^。！？!?\\n]{0,10}(?:最终目标|主线目标|终极目标)"),
    Regex("大功告成"),
)

private val NEGATION_MARKERS = listOf(
    "没有",
    "没能",
    "未能",
    "尚未",
    "还没",
    "未达成",
    "未完成",
    "未实现",
    "未做到",
    "暂未",
    "无法",
    "不能",
    "差点",
    "险些",
    "差一点",
    "并未",
    "不曾",
    "白白",
)

fun detectGoalAchievedFromText(text: String): Boolean {
    return text.split(Regex("[。！？!?\\n]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .any { sentence ->
            NEGATION_MARKERS.none { sentence.contains(it) } &&
                GOAL_COMPLETION_PATTERNS.any { it.containsMatchIn(sentence) }
        }
}
