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

/** 生命值收敛到 0-100。 */
const val STAT_MIN = 0
const val STAT_MAX = 100

/** 新故事一律满血开局。 */
val DEFAULT_STATS = StatValues(health = STAT_MAX)

/**
 * 每回合生命值变化的体感上限（非结局回合）。
 * 不做硬性「一刀 40 血」的斩杀，让 100 点生命至少能撑过若干次失误；
 * 濒危时反而放宽治疗、收紧伤害，避免低血量直接进入死亡螺旋。
 */
const val HEALTH_LOSS_CAP = 18
const val HEALTH_GAIN_CAP = 12
const val LOW_HEALTH_THRESHOLD = 30
const val LOW_HEALTH_LOSS_CAP = 10
const val LOW_HEALTH_GAIN_CAP = 20

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

/** 开场血条固定为满，不再沿用模型给的初始值。 */
fun openingStats(value: StatValues? = null): StatValues = StatValues(health = STAT_MAX)

fun applyStatChange(
    base: StatValues,
    change: StatChange,
    final: Boolean = false,
): StatValues {
    val low = base.health <= LOW_HEALTH_THRESHOLD
    val loss = if (low) LOW_HEALTH_LOSS_CAP else HEALTH_LOSS_CAP
    val gain = if (low) LOW_HEALTH_GAIN_CAP else HEALTH_GAIN_CAP
    val delta = if (final) {
        clamp(change.health, -STAT_MAX, STAT_MAX)
    } else {
        clamp(change.health, -loss, gain)
    }
    return StatValues(health = clamp(base.health + delta, STAT_MIN, STAT_MAX))
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
