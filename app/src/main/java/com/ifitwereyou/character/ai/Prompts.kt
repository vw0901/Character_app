package com.ifitwereyou.character.ai

import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CheckIssue
import com.ifitwereyou.character.data.CheckResult
import com.ifitwereyou.character.data.GameState
import com.ifitwereyou.character.data.HistoryEntry
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.ProjectCodec
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.SearchResult
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryTimeline
import com.ifitwereyou.character.data.asArray
import com.ifitwereyou.character.data.asRecord
import com.ifitwereyou.character.data.clipCodePoints
import com.ifitwereyou.character.data.normalizeBible
import com.ifitwereyou.character.data.parseJsonLoose
import com.ifitwereyou.character.data.text
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val content: String)

object Prompts {

    // ------------------------------------------------------------------
    // 检索
    // ------------------------------------------------------------------

    private const val SUMMARY_SYSTEM_PROMPT = """你是一位严谨的小说资料整理助手。请仅依据提供的检索结果，整理小说作者、完结状态、类型、世界观设定、主要人物等已知信息。
不要编造检索结果中不存在的事实，不确定的信息请明确标注为“未确认”。
输出 2-4 段简洁的中文摘要，不要输出 JSON，不要输出 markdown 标题。"""

    private const val FALLBACK_SYSTEM_PROMPT = """你是一位博学的小说资料助手。用户没有提供联网检索结果，请基于你的已有知识介绍这部小说。
请严格输出以下 JSON，不要输出 markdown 代码块或额外文字：
{
  "summary": "关于作者、完结状态、类型、世界观、主要人物和核心设定的中文摘要；不确定的信息明确标注为“未确认”"
}"""

    fun searchSummaryMessages(query: String, results: List<SearchResult>): List<ChatMessage> {
        val snippets = results.take(10).mapIndexed { index, item ->
            "${index + 1}. ${item.title}\nURL: ${item.url}\n${item.snippet}"
        }.joinToString("\n\n")
        return listOf(
            ChatMessage("system", SUMMARY_SYSTEM_PROMPT),
            ChatMessage("user", "检索词：$query\n\n检索结果：\n$snippets"),
        )
    }

    fun searchFallbackMessages(novelName: String): List<ChatMessage> = listOf(
        ChatMessage("system", FALLBACK_SYSTEM_PROMPT),
        ChatMessage("user", "请介绍小说《$novelName》的作者、完结状态、类型、世界观设定和主要人物。"),
    )

    fun parseFallbackSummary(raw: String): String {
        val payload = asRecord(parseJsonLoose(raw))
        val summary = payload?.let { text(it.opt("summary")) }.orEmpty()
        if (summary.isNotBlank()) return summary.trim()
        val direct = try {
            text(JSONObject(raw).opt("summary"))
        } catch (_: Exception) {
            ""
        }
        return direct.ifBlank { raw.trim() }
    }

    // ------------------------------------------------------------------
    // 设定圣经
    // ------------------------------------------------------------------

    private const val BIBLE_SYSTEM_PROMPT = """你是一位专业的小说解析师，擅长从小说中提取结构化的「设定圣经」。
你将根据用户提供的小说名称和搜索结果，生成一份完整的设定圣经。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "novelName": "小说名",
  "author": "作者",
  "status": "完结/连载",
  "genre": "类型",
  "synopsis": "简短概述",
  "worldview": {
    "summary": "世界观总述",
    "geography": "地理环境",
    "history": "历史背景",
    "rules": "世界运行规则"
  },
  "powerSystem": {
    "name": "体系名称",
    "levels": ["等级1", "等级2"],
    "description": "体系说明",
    "limitations": "限制条件"
  },
  "factions": [
    {
      "name": "势力名",
      "description": "描述",
      "keyMembers": ["成员"],
      "goal": "目标"
    }
  ],
  "characters": [
    {
      "name": "角色名",
      "role": "角色定位",
      "abilities": "能力",
      "personality": "性格",
      "relationships": "关系网"
    }
  ],
  "timeline": [
    {
      "event": "事件",
      "description": "描述"
    }
  ],
  "mysteries": [
    {
      "question": "未解之谜",
      "clues": "线索",
      "theories": "猜测"
    }
  ]
}

要求：
1. 如果搜索结果中信息不足，可以基于小说类型和已知信息进行合理推演，但要标注“推演”
2. 每个部分都要有实质内容，不要留空
3. 人物至少5个，势力至少3个，时间线至少5个事件，未解之谜至少3个
4. 直接输出 JSON，不要包含任何其他文字"""

    fun bibleMessages(
        novelName: String,
        searchResults: List<SearchResult>,
        searchSummary: String,
    ): List<ChatMessage> {
        val results = searchResults.take(5).mapIndexed { index, result ->
            "${index + 1}. ${result.title}\n   ${result.snippet}"
        }.joinToString("\n\n")
        val userContent = "小说名: $novelName\n" +
            "搜索摘要: ${searchSummary.ifBlank { "无" }}\n" +
            "搜索结果:\n$results"
        return listOf(
            ChatMessage("system", BIBLE_SYSTEM_PROMPT),
            ChatMessage("user", userContent),
        )
    }

    // ------------------------------------------------------------------
    // 漏洞体检
    // ------------------------------------------------------------------

    private const val CHECK_SYSTEM_PROMPT = """你是一位严谨的小说设定审计师，专门对小说的「设定圣经」进行漏洞体检。

你需要从以下维度审查设定的一致性和合理性：
1. **世界观漏洞**: 世界规则是否自相矛盾？地理/历史是否存在逻辑断层？
2. **力量体系漏洞**: 等级设定是否合理？是否存在无法解释的强度崩坏？限制条件是否被滥用？
3. **势力平衡漏洞**: 各势力之间是否存在不合理的强度差？势力动态是否缺乏动力？
4. **人物漏洞**: 角色能力是否与其定位匹配？人物关系是否存在矛盾？是否缺少关键角色？
5. **时间线漏洞**: 事件顺序是否合理？是否存在时间线冲突？
6. **未解之谜**: 线索是否充分？是否存在互相矛盾的猜测？

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "overallScore": 75,
  "summary": "总体评价",
  "issues": [
    {
      "category": "世界观/力量体系/势力平衡/人物/时间线/未解之谜",
      "severity": "高/中/低",
      "title": "漏洞标题",
      "description": "详细描述",
      "fix": "修补方案"
    }
  ],
  "strengths": ["优点1", "优点2"]
}

要求：
1. 至少找出5个漏洞，至少2个优点
2. 每个漏洞都要给出具体可采纳的修补方案
3. 漏洞必须来自圣经里真实存在的内容，不要虚构圣经中没有的设定来凑数
4. 评分范围 0-100
5. 直接输出 JSON，不要包含任何其他文字"""

    fun checkMessages(bible: SettingBible): List<ChatMessage> {
        val bibleJson = ProjectCodec.bibleToJson(bible).toString()
        return listOf(
            ChatMessage("system", CHECK_SYSTEM_PROMPT),
            ChatMessage("user", "请对以下设定圣经进行漏洞体检：\n\n$bibleJson"),
        )
    }

    // ------------------------------------------------------------------
    // 设定修补
    // ------------------------------------------------------------------

    private const val REPAIR_SYSTEM_PROMPT = """你是一位资深的小说设定修补师。你会收到一份「设定圣经」和一份「漏洞体检报告」，
请按照报告中的每条修补方案，直接改写、补全设定圣经，让它自洽完整，可以直接支撑后续的文字冒险。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "summary": "本次修补的整体说明（100 字以内）",
  "appliedFixes": [
    {
      "issue": "对应的漏洞标题",
      "action": "你具体改了什么",
      "field": "worldview / powerSystem / factions / characters / timeline / mysteries"
    }
  ],
  "bible": {
    "novelName": "小说名",
    "author": "作者",
    "status": "完结/连载",
    "genre": "类型",
    "synopsis": "简短概述",
    "worldview": {
      "summary": "世界观总述",
      "geography": "地理环境",
      "history": "历史背景",
      "rules": "世界运行规则"
    },
    "powerSystem": {
      "name": "体系名称",
      "levels": ["等级1", "等级2"],
      "description": "体系说明",
      "limitations": "限制条件"
    },
    "factions": [
      {
        "name": "势力名",
        "description": "描述",
        "keyMembers": ["成员"],
        "goal": "目标"
      }
    ],
    "characters": [
      {
        "name": "角色名",
        "role": "角色定位",
        "abilities": "能力",
        "personality": "性格",
        "relationships": "关系网"
      }
    ],
    "timeline": [
      {
        "event": "事件",
        "description": "描述"
      }
    ],
    "mysteries": [
      {
        "question": "未解之谜",
        "clues": "线索",
        "theories": "猜测"
      }
    ]
  }
}

要求：
1. bible 必须是完整结构，worldview、powerSystem、factions、characters、timeline、mysteries 一个都不能少
2. 保留原设定中未被指出问题的内容，不要删减已有的人物、势力、时间线与未解之谜
3. 体检报告中「高」「中」级别的漏洞都必须被修补，并且修补结果要体现在 bible 里
4. 修补后人物不少于 5 个、势力不少于 3 个、时间线不少于 5 个事件、未解之谜不少于 3 个
5. appliedFixes 与漏洞一一对应，最多 10 条；如果报告里没有漏洞，就补全缺失的部分并在 summary 中说明
6. 修补后的设定仍必须属于这部小说：不要用通用奇幻/科幻模板替换原有世界观
7. 直接输出 JSON，不要包含任何其他文字"""

    private fun compactIssues(check: CheckResult?): List<CheckIssue> =
        check?.issues?.take(10) ?: emptyList()

    fun repairMessages(bible: SettingBible, check: CheckResult?): List<ChatMessage> {
        val bibleJson = ProjectCodec.bibleToJson(bible).toString()

        val report = JSONObject().apply {
            put("overallScore", check?.overallScore ?: JSONObject.NULL)
            put("summary", check?.summary.orEmpty())
            put("issues", JSONArray().apply {
                compactIssues(check).forEach { issue ->
                    put(JSONObject().apply {
                        put("category", issue.category)
                        put("severity", issue.severity)
                        put("title", issue.title)
                        put("description", issue.description)
                        put("fix", issue.fix)
                    })
                }
            })
        }
        val reportJson = report.toString()
        val userContent = "设定圣经：\n$bibleJson\n\n" +
            "漏洞体检报告：\n$reportJson\n\n" +
            "请按体检报告逐条修补，输出完整的修补后设定圣经。"
        return listOf(
            ChatMessage("system", REPAIR_SYSTEM_PROMPT),
            ChatMessage("user", userContent),
        )
    }

    // ------------------------------------------------------------------
    // 冒险开场
    // ------------------------------------------------------------------

    private const val GAME_SYSTEM_PROMPT = """你是一位文字冒险游戏大师，擅长将小说设定转化为沉浸式的文字冒险游戏。

根据提供的设定圣经，生成游戏的初始场景，并为玩家扮演的角色定下最终目标与时间线。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "title": "游戏标题",
  "intro": "开场引子（100-200字，有氛围感）",
  "finalGoal": {
    "title": "最终目标（20字以内，具体可判定）",
    "description": "达成条件（50字以内）"
  },
  "timeline": {
    "label": "时间线名称（如：血月降临前）",
    "totalDays": 20,
    "currentDay": 1
  },
  "playerCharacter": {
    "name": "角色名",
    "background": "背景故事",
    "initialStats": {
      "health": 100,
      "power": 50,
      "reputation": 50
    }
  },
  "currentScene": {
    "id": "scene_1",
    "location": "场景地点",
    "description": "场景描述（150-300字，有画面感）",
    "choices": [
      {
        "id": "choice_1",
        "text": "选项文字",
        "hint": "风险提示（可选）"
      },
      {
        "id": "choice_2",
        "text": "选项文字",
        "hint": "风险提示"
      },
      {
        "id": "choice_3",
        "text": "选项文字",
        "hint": "风险提示"
      }
    ]
  }
}

要求：
1. 开场要有强烈的氛围感，让玩家立刻沉浸
2. 场景描述要生动具体，调动多种感官，且不要与 intro 重复
3. 提供 2-4 个选项，每个选项都必须是玩家此刻立刻能执行的具体行动，不能出现空选项
4. 角色背景要与小说世界观贴合
5. finalGoal 必须为这个角色量身定制、具体可判定（例如「在祭典前找到失踪的妹妹」），不要写「变强」「活下去」这类空泛目标；description 写清楚达成与失败的判断条件
6. finalGoal 必须是这个角色在 totalDays 天内、靠本次冒险中的若干次行动就能完成的事；不要设置需要多年修炼、时间跳跃，或依赖玩家不可能拿到的资源与地点的目标
7. timeline 是本局故事的倒计时：label 说明时间压力来自什么事件，totalDays 取 15-30 的整数，currentDay 固定为 1
8. playerCharacter.initialStats 三项都是 0-100 的整数：health 取 60-100，power 与 reputation 取 20-80；不要让角色一开局就濒死
9. 直接输出 JSON，不要包含任何其他文字"""

    fun gameMessages(bible: SettingBible, character: CharacterChoice?): List<ChatMessage> {
        val characterContext = if (character != null) {
            buildList {
                add("玩家选择扮演以下角色进入故事：")
                add("扮演角色: ${character.name}")
                add("角色背景: ${character.background}")
                character.abilities?.takeIf { it.isNotBlank() }?.let { add("角色能力: $it") }
                character.personality?.takeIf { it.isNotBlank() }?.let { add("角色性格: $it") }
                add("")
                add("请以这个角色的第一人称视角生成开场，把玩家就是这个角色本人。开场叙述和场景描述都要紧密结合这个角色的身份、能力与性格，令场景之间的进入脱离不会与角色设定冲突。")
                add("finalGoal 必须是这个角色自己最想完成的事，而不是泛泛的世界危机。")
            }.joinToString("\n")
        } else {
            "玩家将以原创观察者身份进入这个世界，你可以为玩家分配一个适合世界观的自由角色身份" +
                "（playerCharacter.name 为玩家角色名）。"
        }

        val bibleJson = ProjectCodec.bibleToJson(bible).toString()
        val userContent = "请根据以下设定圣经生成文字冒险游戏的初始场景：\n\n$bibleJson\n\n$characterContext"
        return listOf(
            ChatMessage("system", GAME_SYSTEM_PROMPT),
            ChatMessage("user", userContent),
        )
    }

    // ------------------------------------------------------------------
    // 冒险推进
    // ------------------------------------------------------------------

    private const val GAME_ACTION_SYSTEM_PROMPT = """你是一位文字冒险游戏大师。玩家刚刚做出了一个选择，你需要生成下一个场景，并推进故事的时间线。

根据玩家的选择，生成新的场景、新的选项，以及本次行动消耗的天数。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "narration": "对玩家选择的结果叙述（100-200字）",
  "currentScene": {
    "id": "scene_N",
    "location": "新场景地点",
    "description": "新场景描述（150-300字）",
    "choices": [
      {
        "id": "choice_N_1",
        "text": "选项文字",
        "hint": "风险提示"
      },
      {
        "id": "choice_N_2",
        "text": "选项文字",
        "hint": "风险提示"
      },
      {
        "id": "choice_N_3",
        "text": "选项文字",
        "hint": "风险提示"
      }
    ]
  },
  "statsChange": {
    "health": 0,
    "power": 0,
    "reputation": 0
  },
  "daysPassed": 1,
  "goalAchieved": false,
  "isEnding": false,
  "endingType": null,
  "endingText": "如果是结局，这里写结局文字（可选）"
}

要求：
1. 叙述要紧承「最近剧情」与玩家这次的选择，只写这次选择带来的结果与变化；不要复述已经发生过的事件，也不要把新场景的环境描写提前复述一遍
2. 新场景要有新的发现和挑战；选项不要与上一场景重复，也不要把玩家刚做过的选择原样再给一次
3. 每次给出 2-4 个玩家此刻立刻能执行的新选项，绝不能没有选项
4. statsChange 是本次行动对生命/战力/声望的加减，三项都写整数，没有变化写 0
5. 叙述与数值必须一致：轻微擦伤 -5~-10，重伤 -25~-40，休息治疗 +10~+20；不要「只是擦破皮」却扣掉 30 点生命
6. 非结局回合的单次变化不能超出：health -40~+25，power -30~+30，reputation -30~+30；结局回合可以按结局叙述给出更大的变化
7. daysPassed 表示这次行动消耗的天数，取 1-3 的整数；不要自己修改当前日期，前端会累加
8. 每轮生成前先对照「最终目标 / 达成条件」核对：只要本轮 narration、currentScene.description 或 endingText 里已经在事实上完成了目标（条件全部满足，哪怕没出现「成功」二字），就必须立刻收束——goalAchieved=true、isEnding=true、endingType="success"，并在 endingText 写出成功结局；不能因为想继续写新场景而把它设为 false，也不能把已经完成的目标留到下一轮
9. 如果本次 statsChange 会让生命值降到 0 或以下，isEnding=true、endingType="failure"，并写出失败结局；生命归零由前端最终裁定
10. 时间线耗尽也由前端裁定失败，你不要因为剩余天数少就提前结束故事；但当剩余天数 ≤ 3 时，选项必须提供一条能直接冲向最终目标的路径
11. 如果这次是玩家自己写下的行动（ID 以 custom 开头），必须让它真的发生：能做成就写过程与代价，做不到就写受阻、失败或引出新的麻烦；既不能无视它，也不能替玩家追加他没有写过的举动
12. 除第 8、9 条的情况外，isEnding 必须为 false；直接输出 JSON，不要包含任何其他文字"""

    fun gameActionMessages(
        bible: SettingBible,
        gameState: GameState,
        stats: StatValues,
        goal: StoryGoal,
        timeline: StoryTimeline,
        choiceId: String,
        choiceText: String,
        character: CharacterChoice?,
        history: List<HistoryEntry>,
    ): List<ChatMessage> {
        val characterContext = buildString {
            if (character != null) {
                append("\n\n玩家扮演的角色: ${character.name}\n")
                append("角色背景: ${character.background}\n")
                character.abilities?.takeIf { it.isNotBlank() }?.let { append("角色能力: $it\n") }
                character.personality?.takeIf { it.isNotBlank() }?.let { append("角色性格: $it\n") }
                append("请始终以这个角色的第一人称视角编写叙述，保持其身份与能力设定。")
            }
        }

        val goalContext = buildString {
            if (goal.title.isNotBlank()) {
                append("\n\n最终目标: ${goal.title}")
                if (goal.description.isNotBlank()) append("\n达成条件: ${goal.description}")
            }
        }

        val remaining = maxOf(0, timeline.totalDays - timeline.currentDay)
        val timelineContext = if (timeline.totalDays > 0) {
            "\n\n时间线：${timeline.label.ifBlank { "主线" }}，当前第 ${timeline.currentDay} / ${timeline.totalDays} 天，剩余 $remaining 天。"
        } else {
            ""
        }

        val gameStateJson = JSONObject().apply {
            put("title", gameState.title)
            put("intro", gameState.intro)
            put("playerCharacter", JSONObject().apply {
                put("name", gameState.playerCharacter.name)
                put("background", gameState.playerCharacter.background)
                put("initialStats", ProjectCodec.statsToJson(gameState.playerCharacter.initialStats))
            })
            put("currentScene", ProjectCodec.sceneToJson(gameState.currentScene))
            put("stats", ProjectCodec.statsToJson(stats))
            put("goal", ProjectCodec.goalToJson(goal))
            put("timeline", ProjectCodec.timelineToJson(timeline))
        }

        val sections = mutableListOf("设定摘要: ${bibleDigest(bible).toString()}")
        val recent = formatHistory(history, limit = 8, textLimit = 300)
        if (recent.isNotEmpty()) sections.add("最近剧情（从早到晚）:\n$recent")
        sections.add("当前游戏状态: ${gameStateJson.toString()}")
        val actionLine = if (choiceId.startsWith("custom")) {
            "玩家自己写下的行动: $choiceText (ID: $choiceId)"
        } else {
            "玩家选择了: $choiceText (ID: $choiceId)"
        }
        sections.add("$actionLine$characterContext$goalContext$timelineContext")

        return listOf(
            ChatMessage("system", GAME_ACTION_SYSTEM_PROMPT),
            ChatMessage("user", sections.joinToString("\n\n") + "\n\n请生成下一个场景。"),
        )
    }

    // ------------------------------------------------------------------
    // 最终目标判定
    // ------------------------------------------------------------------

    private const val GOAL_CHECK_SYSTEM_PROMPT = """你是一位严格的文字冒险剧情审校员。你的唯一任务：判断「本轮剧情」是否已经在事实上完成了玩家的最终目标。

判定标准：
1. 只看剧情里发生的事实，不看叙述自称的「成功」「完成」；也不要求出现「成功」二字。
2. 最终目标的达成条件必须全部满足。只是接近目标、拿到关键线索、承诺接下来去做、推进了一步，都不算完成。
3. 如果之前的剧情已经完成了目标，而本轮只是继续叙述后续，则判为已完成。
4. 如果本轮叙述明确写出目标失败、放弃、错过时机，且达成条件没有满足，则判为未完成。
5. 判断要保守：证据不明确时判 false，宁可不结束，也不要误判成功。
6. 玩家的选择只是意图，不是已经发生的事实；只有本轮叙述里写成事实的进展才算数。
7. reason 用一句不超过 40 字的中文说明依据。

只输出 JSON，不要 markdown 代码块，不要输出任何其他文字：
{"achieved": true, "confidence": "high", "reason": "本轮叙述中玩家已经找到并救出妹妹"}

confidence 取 high / medium / low。"""

    fun goalCheckMessages(
        goal: StoryGoal,
        narration: String,
        scene: Scene?,
        history: List<HistoryEntry>,
    ): List<ChatMessage> {
        val title = clipCodePoints(goal.title, 120).ifBlank { "（未命名目标）" }
        val description = clipCodePoints(goal.description, 600)
        val lines = mutableListOf("【最终目标】", title)
        if (description.isNotEmpty()) {
            lines.add("")
            lines.add("【达成条件】")
            lines.add(description)
        }
        val recent = formatHistory(history, limit = 8, textLimit = 600)
        if (recent.isNotEmpty()) {
            lines.add("")
            lines.add("【之前的剧情（仅作背景）】")
            lines.add(recent)
        }
        lines.add("")
        lines.add("【本轮行动结果叙述】")
        lines.add(clipCodePoints(narration, 600).ifBlank { "（本轮没有叙述）" })

        val sceneText = formatScene(scene, descriptionLimit = 600)
        if (sceneText.isNotEmpty()) {
            lines.add("")
            lines.add("【本轮进入的新场景】")
            lines.add(sceneText)
        }
        lines.add("")
        lines.add("请判断：本轮剧情是否已经在事实上完成了最终目标？只输出 JSON。")
        return listOf(
            ChatMessage("system", GOAL_CHECK_SYSTEM_PROMPT),
            ChatMessage("user", lines.joinToString("\n")),
        )
    }

    // ------------------------------------------------------------------
    // 行动润色
    // ------------------------------------------------------------------

    private const val POLISH_SYSTEM_PROMPT = """你是一位文字冒险游戏的动作润色师。玩家会自己写下一句想做的事，你把它润色成一条可以交给故事大师的行动描述。

要求：
1. 保留玩家的原意、对象和语气，不新增玩家没有表达过的动作、物品、地点或人物
2. 改写成第一人称「我」此刻正在做的动作，1-2 句，不超过 80 字
3. 可以补充动作的方式、姿态或感官细节，但不要写出结果，也不要替玩家决定成败
4. 不要出现「我选择」「玩家」「行动：」这类说明性文字，不要加引号、序号或 markdown
5. 只输出润色后的这一句：不解释、不反问、不提供选项

玩家的表达已经通顺时，在保持原意的前提下略作提炼即可，不要为了改而改。"""

    private val POLISH_LABELS = listOf(
        "润色后：", "润色后:", "润色：", "润色:", "改写：", "改写:", "行动：", "行动:", "结果：",
    )

    fun cleanPolish(raw: String): String {
        var text = raw.trim().replace(Regex("```[a-zA-Z]*"), "").trim()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        text = lines.first()
        val label = POLISH_LABELS.firstOrNull { text.startsWith(it) }
        if (label != null) text = text.removePrefix(label).trim()
        text = text.trim('“', '”', '"', '\'', '「', '」', '『', '』', ' ')
        return text.take(200)
    }

    fun polishMessages(
        bible: SettingBible,
        character: CharacterChoice?,
        goal: StoryGoal?,
        scene: Scene?,
        text: String,
        history: List<HistoryEntry>,
    ): List<ChatMessage> {
        val novel = clipCodePoints(bible.novelName, 120)
        val genre = clipCodePoints(bible.genre, 40)
        val sections = mutableListOf<String>()
        if (novel.isNotEmpty()) sections.add("【作品】\n$novel" + if (genre.isNotEmpty()) "（$genre）" else "")

        val context = listOfNotNull(
            formatScene(scene, descriptionLimit = 200),
            formatCharacter(character),
        ).joinToString("\n\n")
        if (context.isNotEmpty()) sections.add("【玩家的处境】\n$context")

        val title = clipCodePoints(goal?.title, 120)
        if (title.isNotEmpty()) sections.add("【最终目标】\n$title")

        val recent = formatHistory(history, limit = 6, textLimit = 200)
        if (recent.isNotEmpty()) sections.add("【最近剧情（从早到晚）】\n$recent")

        sections.add("【玩家写下的行动】\n${clipCodePoints(text, 200)}")
        sections.add("请把这条行动润色成可以直接写进故事的一句话。")
        return listOf(
            ChatMessage("system", POLISH_SYSTEM_PROMPT),
            ChatMessage("user", sections.joinToString("\n\n")),
        )
    }

    // ------------------------------------------------------------------
    // 后日谈
    // ------------------------------------------------------------------

    private const val EPILOGUE_SYSTEM_PROMPT = """你是一位擅长写「后日谈」的叙事作家。玩家的这段文字冒险已经结束，你要为它写一篇后日谈，作为整段故事的大结局。

后日谈写的是「结局之后」：
1. 用 3-5 段中文，总共 500-800 字：先回望这段路上真正改变了什么，再写此后人们怎样活下去。
2. 成功结局写目标达成后的余波：代价、失去的东西、被救下的人；失败结局写未竟之事怎样留在世界里，以及角色最后停在哪里。不要改写已经发生的结局，也不要给玩家一次翻盘的机会。
3. 沿用第二人称「你」，与正文保持同一种语气和世界观；不要引入设定之外的新角色、新势力或新能力。
4. 交代主要人物与关键线索的下落，让读者感到这个故事真的收尾了。
5. 可以留一点余韵，但不要抛出新的选择、任务或「下一章」式的悬念。
6. 直接输出后日谈正文：不要标题、不要 markdown 标记、不要解释你写了什么。"""

    fun epilogueMessages(
        bible: SettingBible,
        character: CharacterChoice?,
        goal: StoryGoal,
        timeline: StoryTimeline?,
        outcome: String,
        outcomeReason: String,
        endingText: String,
        history: List<HistoryEntry>,
    ): List<ChatMessage> {
        val novel = clipCodePoints(bible.novelName, 120)
        val sections = mutableListOf<String>()
        if (novel.isNotEmpty()) sections.add("【作品】\n$novel")

        val storyContext = listOfNotNull(
            formatCharacter(character),
            formatGoal(goal),
            formatTimeline(timeline),
            formatOutcome(outcome, outcomeReason, endingText),
        ).joinToString("\n\n")
        if (storyContext.isNotEmpty()) sections.add("【这一局的关键信息】\n$storyContext")

        val replay = formatHistory(history, limit = 12, textLimit = 500)
        if (replay.isNotEmpty()) sections.add("【故事回顾（节选）】\n$replay")

        sections.add("请为这段故事写后日谈：交代结局之后的余波，给整段冒险一个真正的收尾。")
        return listOf(
            ChatMessage("system", EPILOGUE_SYSTEM_PROMPT),
            ChatMessage("user", sections.joinToString("\n\n")),
        )
    }

    // ------------------------------------------------------------------
    // 连接测试
    // ------------------------------------------------------------------

    fun aiTestMessages(): List<ChatMessage> = listOf(
        ChatMessage("system", "你是 AI 服务连通性测试助手，请只回复一句简短确认。"),
        ChatMessage("user", "请回复：连接成功"),
    )

    // ------------------------------------------------------------------
    // 共用上下文整理
    // ------------------------------------------------------------------

    fun formatHistory(
        history: List<HistoryEntry>?,
        limit: Int = 8,
        textLimit: Int = 300,
    ): String {
        return (history ?: emptyList()).takeLast(limit).mapNotNull { item ->
            val content = clipCodePoints(item.text, textLimit)
            if (content.isEmpty()) return@mapNotNull null
            val kind = if (item.type == HistoryType.CHOICE) "玩家选择" else "剧情"
            "- $kind：$content"
        }.joinToString("\n")
    }

    private fun formatCharacter(character: CharacterChoice?): String {
        if (character == null) return ""
        val lines = mutableListOf("角色：${character.name}")
        if (character.background.isNotBlank()) lines.add("背景：${clipCodePoints(character.background, 120)}")
        character.abilities?.takeIf { it.isNotBlank() }?.let { lines.add("能力：${clipCodePoints(it, 120)}") }
        return lines.joinToString("\n")
    }

    private fun formatScene(scene: Scene?, descriptionLimit: Int): String {
        if (scene == null) return ""
        val location = clipCodePoints(scene.location, 80)
        val description = clipCodePoints(scene.description, descriptionLimit)
        return when {
            location.isNotEmpty() && description.isNotEmpty() -> "位置：$location\n场景：$description"
            location.isNotEmpty() -> "位置：$location"
            description.isNotEmpty() -> "场景：$description"
            else -> ""
        }
    }

    private fun formatGoal(goal: StoryGoal): String {
        val title = clipCodePoints(goal.title, 120).ifBlank { "（未命名目标）" }
        val description = clipCodePoints(goal.description, 600)
        return if (description.isNotEmpty()) "最终目标：$title\n达成条件：$description" else "最终目标：$title"
    }

    private fun formatTimeline(timeline: StoryTimeline?): String {
        if (timeline == null) return ""
        val label = clipCodePoints(timeline.label, 40).ifBlank { "主线" }
        return "时间线：$label，故事停在第 ${timeline.currentDay} / ${timeline.totalDays} 天"
    }

    private fun formatOutcome(outcome: String, reason: String, endingText: String): String {
        val label = if (outcome == "success") "成功达成最终目标" else "失败"
        val lines = mutableListOf("结局：$label")
        if (reason.isNotBlank()) lines.add("结局原因：${clipCodePoints(reason, 200)}")
        if (endingText.isNotBlank()) lines.add("结局正文：${clipCodePoints(endingText, 800)}")
        return lines.joinToString("\n")
    }

    /** 把设定圣经压缩成冒险每轮都要带的「设定摘要」。 */
    fun bibleDigest(bible: SettingBible): JSONObject {
        val digest = JSONObject()
        digest.put("novelName", clipCodePoints(bible.novelName, 80))
        digest.put("genre", clipCodePoints(bible.genre, 40))
        digest.put("synopsis", clipCodePoints(bible.synopsis, 300))
        digest.put(
            "worldview",
            clipCodePoints(
                bible.worldview.summary.ifBlank { bible.worldview.rules },
                400,
            ),
        )

        val power = JSONObject()
        power.put("name", clipCodePoints(bible.powerSystem.name, 40))
        power.put("levels", JSONArray(bible.powerSystem.levels.take(6).map { it.trim() }.filter { it.isNotEmpty() }))
        power.put("limitations", clipCodePoints(bible.powerSystem.limitations, 200))
        if (bible.powerSystem.name.isNotBlank() || bible.powerSystem.levels.isNotEmpty() || bible.powerSystem.limitations.isNotBlank()) {
            digest.put("powerSystem", power)
        }

        val factions = JSONArray()
        bible.factions.take(6).forEach { faction ->
            factions.put(JSONObject().apply {
                put("name", clipCodePoints(faction.name, 40))
                put("goal", clipCodePoints(faction.goal, 120))
            })
        }
        if (factions.length() > 0) digest.put("factions", factions)

        val characters = JSONArray()
        bible.characters.take(8).forEach { character ->
            characters.put(JSONObject().apply {
                put("name", clipCodePoints(character.name, 40))
                put("role", clipCodePoints(character.role, 60))
                put("abilities", clipCodePoints(character.abilities, 100))
            })
        }
        if (characters.length() > 0) digest.put("characters", characters)

        val mysteries = JSONArray()
        bible.mysteries.take(5).forEach { mystery ->
            if (mystery.question.isNotBlank()) mysteries.put(clipCodePoints(mystery.question, 120))
        }
        if (mysteries.length() > 0) digest.put("mysteries", mysteries)
        return digest
    }
}
