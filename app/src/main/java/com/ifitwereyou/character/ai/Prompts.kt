package com.ifitwereyou.character.ai

import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.GameState
import com.ifitwereyou.character.data.HistoryEntry
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.ProjectCodec
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.SearchResult
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryOption
import com.ifitwereyou.character.data.StoryTimeline
import com.ifitwereyou.character.data.asRecord
import com.ifitwereyou.character.data.clipCodePoints
import com.ifitwereyou.character.data.parseJsonLoose
import com.ifitwereyou.character.data.text
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val content: String)

object Prompts {

    // ------------------------------------------------------------------
    // 检索
    // ------------------------------------------------------------------

    private const val SUMMARY_SYSTEM_PROMPT = """你是一位严谨的小说考据助手。请仅依据提供的检索结果（可参考 Tavily 的 answer），整理一部小说的「考据资料包」。

需要覆盖：
1. 基本盘：作者、连载/完结、类型、篇幅、出版或连载时间
2. 世界观：时代/地域、世界运行规则、力量体系与等级
3. 人物：主要人物、身份关系、性格与能力
4. 故事：核心冲突、主线脉络、结局（可剧透）
5. 原著味道：叙事文风、名场面、经典台词（尽量保留原句，标注说话人）

要求：
- 事实优先，按检索结果写；信息不确定时标注「未确认」，不要编造
- 名场面与台词宁可少写，也不要写成通用网文模板
- 输出 600-900 字的中文资料，不要输出 JSON，不要 markdown 标题"""

    private const val FALLBACK_SYSTEM_PROMPT = """你是一位读过大量小说的考据助手。用户没有提供联网检索结果，请基于你的已有知识介绍这部小说。
请严格输出以下 JSON，不要输出 markdown 代码块或额外文字：
{
  "summary": "600-900 字中文资料：作者、完结状态、类型、世界观、力量体系、主要人物关系、核心冲突、结局、叙事文风、名场面与经典台词。不确定的信息明确标注为「未确认」"
}"""

    fun searchSummaryMessages(
        query: String,
        results: List<SearchResult>,
        tavilyAnswer: String = "",
    ): List<ChatMessage> {
        val snippets = results.take(12).mapIndexed { index, item ->
            val snippet = clipCodePoints(item.snippet, 700)
            "${index + 1}. ${item.title}\nURL: ${item.url}\n$snippet"
        }.joinToString("\n\n")
        val answer = tavilyAnswer.trim().let { if (it.isEmpty()) "无" else clipCodePoints(it, 1200) }
        return listOf(
            ChatMessage("system", SUMMARY_SYSTEM_PROMPT),
            ChatMessage("user", "检索词：$query\n\nTavily 摘要：\n$answer\n\n检索结果：\n$snippets"),
        )
    }

    fun searchFallbackMessages(novelName: String): List<ChatMessage> = listOf(
        ChatMessage("system", FALLBACK_SYSTEM_PROMPT),
        ChatMessage(
            "user",
            "请介绍小说《$novelName》：作者、完结状态、类型、世界观、力量体系、" +
                "主要人物关系、核心冲突、结局方向、叙事文风、名场面和经典台词。",
        ),
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

    private const val BIBLE_SYSTEM_PROMPT = """你是一位资深的小说考据师与世界观编辑。请为一部小说提取一份「忠于原著、可以直接支撑文字冒险」的设定圣经。
你会收到小说名、检索摘要与检索结果；请优先使用其中出现的事实、称谓与原文。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "novelName": "小说名",
  "author": "作者",
  "status": "完结/连载",
  "genre": "类型",
  "tone": "叙事文风与语言气质（20-50字，如：冷峻克制的硬科幻，短句、少形容词）",
  "themes": ["主题词1", "主题词2", "主题词3"],
  "synopsis": "200字以内、包含核心冲突与结局方向的概述",
  "worldview": {
    "summary": "世界观总述",
    "geography": "地理环境与重要区域",
    "history": "历史背景与关键纪元",
    "rules": "世界运行规则（什么能做、什么绝对不能做）"
  },
  "powerSystem": {
    "name": "体系名称（用原著叫法）",
    "levels": ["等级1", "等级2", "等级3"],
    "description": "体系说明",
    "limitations": "限制、代价与禁忌"
  },
  "factions": [
    {
      "name": "势力名（用原著叫法）",
      "description": "立场、行事风格与现状",
      "keyMembers": ["成员"],
      "goal": "目标"
    }
  ],
  "characters": [
    {
      "name": "角色名",
      "role": "身份与在故事中的位置",
      "abilities": "能力、武器或专长",
      "personality": "性格与说话方式",
      "relationships": "与关键人物的关系"
    }
  ],
  "timeline": [
    {
      "event": "事件名",
      "description": "前因后果"
    }
  ],
  "mysteries": [
    {
      "question": "未解之谜",
      "clues": "原著给出的线索",
      "theories": "主流猜测"
    }
  ],
  "locations": [
    {
      "name": "重要地点（原著地名）",
      "description": "环境与氛围",
      "significance": "为什么重要、发生过什么"
    }
  ],
  "keyItems": [
    {
      "name": "关键物品/道具/功法/武器",
      "description": "外观与用途",
      "significance": "在故事中的作用"
    }
  ],
  "iconicScenes": [
    {
      "title": "名场面标题",
      "description": "谁、在哪里、发生了什么",
      "impact": "为什么被读者记住"
    }
  ],
  "quotes": [
    {
      "text": "经典台词，尽量保留原文",
      "speaker": "说话人",
      "context": "在什么情境下说出"
    }
  ]
}

要求：
1. 忠于原著：人名、地名、组织名、境界名、招式名、台词尽量沿用原著表述，不要用通用奇幻/科幻模板替换
2. 完整度：人物至少 6 个，势力至少 3 个，时间线至少 6 个事件，未解之谜至少 3 个，地点至少 4 个，关键物品至少 3 个，名场面至少 4 个，台词至少 4 句
3. tone 必须具体到语言气质与叙事节奏；themes 取 3-6 个真正的主题词，不要写「冒险」「成长」这类空话
4. 名场面与台词必须来自这部小说或高度可信的检索资料；句子记不全时可以写「（大意）」，不要把通用台词硬塞进来
5. 资料不足时可以基于世界观合理推演，但要在对应文字末尾标注「（推演）」；不要整段留空
6. synopsis 可以剧透，但不要把结局写成一句话；为后续冒险留出可玩的空间
7. 直接输出 JSON，不要包含任何其他文字"""

    fun bibleMessages(
        novelName: String,
        searchResults: List<SearchResult>,
        searchSummary: String,
    ): List<ChatMessage> {
        val results = searchResults.take(12).mapIndexed { index, result ->
            val snippet = clipCodePoints(result.snippet, 500)
            "${index + 1}. ${result.title}（${result.siteName.ifBlank { result.url }}）\n   $snippet"
        }.joinToString("\n\n")
        val userContent = "小说名: $novelName\n" +
            "检索摘要:\n${clipCodePoints(searchSummary.ifBlank { "无" }, 2000)}\n\n" +
            "检索结果:\n$results"
        return listOf(
            ChatMessage("system", BIBLE_SYSTEM_PROMPT),
            ChatMessage("user", userContent),
        )
    }

    // ------------------------------------------------------------------
    // 角色故事栏
    // ------------------------------------------------------------------

    private const val STORY_OPTIONS_SYSTEM_PROMPT = """你是一位小说改编文字冒险的故事策划。玩家选定了一个角色，请为这个角色构思 3-4 条彼此差异明显、都能独立通关的故事线。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "stories": [
    {
      "title": "故事名（4-14字，有原著味道）",
      "summary": "故事简概（40-70字，写清处境、目标与主要风险，不剧透结局）",
      "opening": "开场切入点（20-50字，第一人称，说明故事从哪个时刻、哪个地点开始）",
      "estimatedTurns": 8,
      "totalDays": 20
    }
  ]
}

要求：
1. 所有故事都以这个角色为主角，沿用原著的人名、地名、组织名、称谓与语言气质，不要写成通用玄幻/都市套路
2. 故事名要像这部小说里真实存在的一段往事，不要用「命运的抉择」「新的旅程」这类空泛标题
3. summary 必须让玩家一眼看懂：我是谁、我要做什么、最大的阻碍是什么；不要把结局写进去
4. opening 要具体到一个可玩的瞬间，不要从「很久以前」讲起，也不要复述 summary
5. 3-4 条故事线必须在切入时间点、地点、目标类型或人物关系上有明显差异，不能只是换一个任务名
6. estimatedTurns 取 6-10 的整数，totalDays 取 15-30 的整数，且 totalDays 不小于 estimatedTurns；这是给玩家看的时长，也是本局时间线
7. 每个故事的目标都要能在这段天数内、靠十来个行动完成，不要设置多年修炼或依赖无法抵达的地点
8. 直接输出 JSON，不要包含任何其他文字"""

    fun storyOptionMessages(bible: SettingBible, character: CharacterChoice): List<ChatMessage> {
        val characterContext = buildString {
            append("玩家选择扮演的角色：\n")
            append("角色名: ${character.name}\n")
            if (character.background.isNotBlank()) append("角色背景: ${character.background}\n")
            character.abilities?.takeIf { it.isNotBlank() }?.let { append("角色能力: $it\n") }
            character.personality?.takeIf { it.isNotBlank() }?.let { append("角色性格: $it\n") }
            append("请只为这个角色设计故事线，不要把视角换成其他人物。")
        }
        val userContent = "请为以下角色设计故事栏：\n\n设定摘要: ${bibleDigest(bible)}\n\n$characterContext"
        return listOf(
            ChatMessage("system", STORY_OPTIONS_SYSTEM_PROMPT),
            ChatMessage("user", userContent),
        )
    }

    // ------------------------------------------------------------------
    // 冒险开场
    // ------------------------------------------------------------------

    private const val GAME_SYSTEM_PROMPT = """你是一位文字冒险游戏大师，擅长将小说设定转化为沉浸式的文字冒险游戏。

你会收到「本条故事线」的故事名、简概、限定天数与开场切入点。请只为这一条故事线生成开场，并让玩家立刻进入那个瞬间。

请严格按以下 JSON 格式输出（不要输出 markdown 代码块标记，直接输出 JSON）：
{
  "title": "故事名（沿用给定故事线标题）",
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
    "background": "背景故事"
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
1. 开场要有强烈的氛围感，从给定切入点直接切入，让玩家立刻沉浸；不要先讲前史
2. 场景描述要生动具体，调动多种感官，且不要与 intro 重复
3. 提供 2-4 个选项，每个选项都必须是玩家此刻立刻能执行的具体行动，不能出现空选项
4. 角色背景要与小说世界观贴合，并以这个角色自己的第一人称视角叙述
5. finalGoal 必须为这个角色量身定制、具体可判定（例如「在祭典前找到失踪的妹妹」），不要写「变强」「活下去」这类空泛目标；description 写清楚达成与失败的判断条件
6. finalGoal 必须能在这条故事线给定的 totalDays 天内、靠十来个行动完成；不要设置需要多年修炼、时间跳跃，或依赖玩家不可能拿到的资源与地点的目标
7. timeline.totalDays 必须等于「本条故事线」给定的天数，currentDay 固定为 1；label 说明时间压力来自什么事件
8. 玩家生命值固定从满值 100 开始，不需要输出任何生命、战力或声望数值；后续只由每回合行动结果改变生命值
9. 必须写出「这部小说」的味道：沿用 tone 的语言气质、人物称呼、地名、组织名与招式名；在合适的地方化用 quotes 与 iconicScenes，但不要生硬复读或把台词塞满
10. 这是该小说的同人冒险，不是通用玄幻/都市爽文：不要出现与世界观无关的现代梗、系统面板或通用打脸桥段
11. 直接输出 JSON，不要包含任何其他文字"""

    fun gameMessages(
        bible: SettingBible,
        character: CharacterChoice?,
        story: StoryOption? = null,
    ): List<ChatMessage> {
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
        val storyContext = story?.let {
            """
            |本条故事线（只能写这一条）：
            |故事名: ${it.title}
            |故事简概: ${it.summary}
            |开场切入点: ${it.opening.ifBlank { "从故事简概描述的那个时刻开始" }}
            |限定天数: ${it.totalDays} 天
            |预估回合: 约 ${it.estimatedTurns} 回合
            |请从切入点直接开场，把故事名用作 title，不要额外引入另一条任务线。
            """.trimMargin()
        }.orEmpty()

        val bibleJson = ProjectCodec.bibleToJson(bible).toString()
        val userContent = "请根据以下设定圣经与故事线生成文字冒险开场：\n\n$bibleJson\n\n$storyContext\n\n$characterContext"
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
    "health": 0
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
4. statsChange 只写健康值 health 一项，没有变化写 0；这是本次行动对生命值的加减
5. 生命值从 100 开始，玩的是「慢慢被消耗」而不是「两下归零」：无伤写 0，擦伤 -3~-8，重伤 -10~-18，休息或治疗 +5~+12
6. 叙述与数值必须一致：只是紧张、被瞪一眼、赶路累了都不扣血；真正受伤才扣血，并且要写出受伤的来源
7. 不要每回合都扣血：连续扣血两三回合后，要给一个喘息、治疗或安全脱身的机会；生命低于 30 时，选项里必须至少有一条撤退、治疗或求援的路
8. 客户端会限制单回合生命变化：高血量时最多 -18、低血量时最多 -10，治疗最多 +12（濒危时 +20）。请让数值自然落在这些范围内，不要依赖一次扣光
9. 非结局回合不要给出超过上限的数值；如果本次行动会让生命值降到 0 或以下，isEnding=true、endingType="failure"，并写出失败结局；生命归零由前端最终裁定
10. daysPassed 表示这次行动消耗的天数，取 1-3 的整数；不要自己修改当前日期，前端会累加
11. 每轮生成前先对照「最终目标 / 达成条件」核对：只要本轮 narration、currentScene.description 或 endingText 里已经在事实上完成了目标（条件全部满足，哪怕没出现「成功」二字），就必须立刻收束——goalAchieved=true、isEnding=true、endingType="success"，并在 endingText 写出成功结局；不能因为想继续写新场景而把它设为 false，也不能把已经完成的目标留到下一轮
12. 时间线耗尽也由前端裁定失败，你不要因为剩余天数少就提前结束故事；但当剩余天数 ≤ 3 时，选项必须提供一条能直接冲向最终目标的路径
13. 如果这次是玩家自己写下的行动（ID 以 custom 开头），必须让它真的发生：能做成就写过程与代价，做不到就写受阻、失败或引出新的麻烦；既不能无视它，也不能替玩家追加他没有写过的举动
14. 保持原著的叙事语气与用词习惯（见设定摘要的 tone / quotes / iconicScenes）：人物说话方式、地名、组织名、招式名不要漂移；可以化用经典台词，但每轮最多一处，且要贴合情境
15. 除第 9、11 条的情况外，isEnding 必须为 false；直接输出 JSON，不要包含任何其他文字"""

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
        story: StoryOption? = null,
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
        if (story != null) {
            sections.add(
                "本条故事线: ${story.title}\n故事简概: ${story.summary}\n开场切入点: ${story.opening}",
            )
        }
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
6. 保持这部小说的叙事文风与用词习惯（tone），不要把它改写成通用网文腔

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
        val tone = clipCodePoints(bible.tone, 120)
        if (tone.isNotEmpty()) sections.add("【原著文风】\n$tone")

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
    // 结局页总结
    // ------------------------------------------------------------------

    private const val ENDING_SUMMARY_SYSTEM_PROMPT = """你是一位负责为文字冒险收束的叙事编辑。故事刚刚结束，请为这次冒险写一页「结局总结」。

结局总结与后日谈不同：它只写故事停下的那一刻，不写此后多年，也不写人物余生。请把它写成 3-5 段、300-500 字的中文正文：
1. 回望玩家以谁的身份、在什么压力下走完了这段路，点出最关键的一两次选择
2. 明确最终目标是否达成；若达成，写清代价与失去；若失败，写清是哪一步让局面无法挽回
3. 交代主要人物、关键线索与重要物品在结局时刻的状态
4. 沿用这部小说的 tone 与用词习惯，可以回扣一次名场面或经典台词，但不要照抄整段

要求：
- 只根据已经发生的剧情总结，不编造未发生的事件，不替玩家追加没有做过的选择
- 不要写「多年以后」「后来人们」这类后日谈内容，也不要抛出新的任务或悬念
- 不要输出标题、markdown、选项或「结局总结：」之类的标签，直接输出正文
- 失败结局也要认真收束，不要写成游戏提示或劝玩家重来"""

    fun endingSummaryMessages(
        bible: SettingBible,
        character: CharacterChoice?,
        story: StoryOption?,
        goal: StoryGoal,
        timeline: StoryTimeline,
        outcome: String,
        outcomeReason: String,
        endingText: String,
        history: List<HistoryEntry>,
    ): List<ChatMessage> {
        val novel = clipCodePoints(bible.novelName, 120)
        val sections = mutableListOf<String>()
        if (novel.isNotEmpty()) sections.add("【作品】\n$novel")
        val tone = clipCodePoints(bible.tone, 120)
        if (tone.isNotEmpty()) sections.add("【原著文风】\n$tone")
        val themes = bible.themes.take(6).joinToString("、")
        if (themes.isNotBlank()) sections.add("【主题】\n$themes")
        if (story != null) {
            sections.add("【本条故事线】\n${story.title}\n${story.summary}")
        }

        val storyContext = listOfNotNull(
            formatCharacter(character),
            formatGoal(goal),
            formatTimeline(timeline),
            formatOutcome(outcome, outcomeReason, endingText),
        ).joinToString("\n\n")
        if (storyContext.isNotEmpty()) sections.add("【这一局的事实】\n$storyContext")

        val replay = formatHistory(history, limit = 14, textLimit = 400)
        if (replay.isNotEmpty()) sections.add("【故事回顾】\n$replay")
        sections.add("请写这一局的结局总结：只写到结局发生的那一刻，为这段冒险收束。")
        return listOf(
            ChatMessage("system", ENDING_SUMMARY_SYSTEM_PROMPT),
            ChatMessage("user", sections.joinToString("\n\n")),
        )
    }

    // ------------------------------------------------------------------
    // 后日谈
    // ------------------------------------------------------------------

    private const val EPILOGUE_SYSTEM_PROMPT = """你是一位擅长写「后日谈」的叙事作家。玩家已经读过了这段故事的结局总结，现在选择继续看「结局之后」发生了什么。

请写一篇独立的后续，而不是把结局重讲一遍：
1. 用 3-5 段中文，总共 500-800 字，第一段直接进入结局之后的日常，不要复述目标与决战过程。
2. 成功结局写目标达成后的余波：代价、失去的东西、被救下的人如何继续生活；失败结局写未竟之事怎样留在世界里，以及角色最后停在哪里。不要改写已经发生的结局，也不要给玩家一次翻盘的机会。
3. 沿用第二人称「你」，保持原著 tone 的语言气质与用词习惯；不要引入设定之外的新角色、新势力或新能力。
4. 交代主要人物与关键线索的下落，让读者感到这个故事真的收尾了；结局总结里已经写过的句子不要重复。
5. 可以回扣一次经典台词或名场面作为余韵，但不要照抄整段，也不要为了煽情硬塞。
6. 可以留一点余韵，但不要抛出新的选择、任务或「下一章」式的悬念。
7. 直接输出后日谈正文：不要标题、不要 markdown 标记、不要解释你写了什么。"""

    fun epilogueMessages(
        bible: SettingBible,
        character: CharacterChoice?,
        goal: StoryGoal,
        timeline: StoryTimeline?,
        outcome: String,
        outcomeReason: String,
        endingText: String,
        history: List<HistoryEntry>,
        endingSummary: String = "",
    ): List<ChatMessage> {
        val novel = clipCodePoints(bible.novelName, 120)
        val sections = mutableListOf<String>()
        if (novel.isNotEmpty()) sections.add("【作品】\n$novel")
        val tone = clipCodePoints(bible.tone, 120)
        if (tone.isNotEmpty()) sections.add("【原著文风】\n$tone")
        val themes = bible.themes.take(6).joinToString("、")
        if (themes.isNotBlank()) sections.add("【主题】\n$themes")

        if (endingSummary.isNotBlank()) {
            sections.add("【结局页总结（已发生，不要重讲）】\n${clipCodePoints(endingSummary, 1200)}")
        }

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
        digest.put("tone", clipCodePoints(bible.tone, 140))
        val themes = bible.themes.take(6).map { it.trim() }.filter { it.isNotEmpty() }
        if (themes.isNotEmpty()) digest.put("themes", JSONArray(themes))
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

        val locations = JSONArray()
        bible.locations.take(5).forEach { location ->
            locations.put(JSONObject().apply {
                put("name", clipCodePoints(location.name, 40))
                put("significance", clipCodePoints(location.significance, 100))
            })
        }
        if (locations.length() > 0) digest.put("locations", locations)

        val items = JSONArray()
        bible.keyItems.take(5).forEach { item ->
            items.put(JSONObject().apply {
                put("name", clipCodePoints(item.name, 40))
                put("significance", clipCodePoints(item.significance, 100))
            })
        }
        if (items.length() > 0) digest.put("keyItems", items)

        val scenes = JSONArray()
        bible.iconicScenes.take(4).forEach { scene ->
            scenes.put(JSONObject().apply {
                put("title", clipCodePoints(scene.title, 50))
                put("description", clipCodePoints(scene.description, 140))
            })
        }
        if (scenes.length() > 0) digest.put("iconicScenes", scenes)

        val quotes = JSONArray()
        bible.quotes.take(4).forEach { quote ->
            quotes.put(JSONObject().apply {
                put("text", clipCodePoints(quote.text, 100))
                put("speaker", clipCodePoints(quote.speaker, 30))
            })
        }
        if (quotes.length() > 0) digest.put("quotes", quotes)
        return digest
    }
}
