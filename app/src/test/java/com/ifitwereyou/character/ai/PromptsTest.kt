package com.ifitwereyou.character.ai

import com.ifitwereyou.character.data.BibleCharacter
import com.ifitwereyou.character.data.CharacterChoice
import com.ifitwereyou.character.data.CharacterMode
import com.ifitwereyou.character.data.ClassicQuote
import com.ifitwereyou.character.data.GameState
import com.ifitwereyou.character.data.IconicScene
import com.ifitwereyou.character.data.KeyItem
import com.ifitwereyou.character.data.KeyLocation
import com.ifitwereyou.character.data.HistoryEntry
import com.ifitwereyou.character.data.HistoryType
import com.ifitwereyou.character.data.PlayerCharacter
import com.ifitwereyou.character.data.Scene
import com.ifitwereyou.character.data.SceneChoice
import com.ifitwereyou.character.data.SearchResult
import com.ifitwereyou.character.data.SettingBible
import com.ifitwereyou.character.data.StatValues
import com.ifitwereyou.character.data.StoryGoal
import com.ifitwereyou.character.data.StoryTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    private val bible = SettingBible(
        novelName = "测试小说",
        genre = "奇幻",
        worldview = com.ifitwereyou.character.data.Worldview(summary = "旧神沉睡的世界"),
        powerSystem = com.ifitwereyou.character.data.PowerSystem(
            name = "古桥体系",
            levels = listOf("一阶", "二阶"),
            limitations = "不能离开桥",
        ),
        characters = listOf(
            BibleCharacter(name = "林澈", role = "游侠", abilities = "剑术", personality = "谨慎"),
        ),
        factions = listOf(
            com.ifitwereyou.character.data.Faction(name = "守桥人", goal = "守住古桥"),
        ),
        mysteries = listOf(
            com.ifitwereyou.character.data.Mystery(question = "桥下有什么？"),
        ),
    )

    private val character = CharacterChoice(
        mode = CharacterMode.EXISTING,
        name = "林澈",
        background = "游侠",
        abilities = "剑术",
        personality = "谨慎",
    )

    private val game = GameState(
        title = "测试冒险",
        intro = "雨夜",
        playerCharacter = PlayerCharacter("林澈", "游侠", StatValues()),
        currentScene = Scene(
            id = "scene_1",
            location = "古桥",
            description = "雨幕中断桥横陈。",
            choices = listOf(SceneChoice("c1", "过桥", "有风险")),
        ),
    )

    @Test
    fun biblePromptIncludesSearchAndName() {
        val messages = Prompts.bibleMessages(
            "测试小说",
            listOf(SearchResult(title = "百科", snippet = "作者是测试作者")),
            "摘要",
        )
        assertEquals("system", messages[0].role)
        assertTrue(messages[0].content.contains("设定圣经"))
        assertTrue(messages[1].content.contains("测试小说"))
        assertTrue(messages[1].content.contains("百科"))
    }

    @Test
    fun gamePromptUsesCharacterIdentity() {
        val messages = Prompts.gameMessages(bible, character)
        assertTrue(messages[1].content.contains("扮演角色: 林澈"))
        assertTrue(messages[1].content.contains("第一人称"))
        assertTrue(messages[1].content.contains("测试小说"))
    }

    @Test
    fun customActionIsMarkedAsPlayerWritten() {
        val history = listOf(HistoryEntry(HistoryType.NARRATION, "雨夜你来到桥边。"))
        val custom = Prompts.gameActionMessages(
            bible = bible,
            gameState = game,
            stats = StatValues(),
            goal = StoryGoal("找到妹妹", "在血月前带她离开"),
            timeline = StoryTimeline("血月降临前", 20, 3),
            choiceId = "custom_2",
            choiceText = "我掀开斗笠",
            character = character,
            history = history,
        )
        val content = custom[1].content
        assertTrue(content.contains("玩家自己写下的行动: 我掀开斗笠"))
        assertTrue(content.contains("最终目标: 找到妹妹"))
        assertTrue(content.contains("剩余 17 天"))
        assertTrue(content.contains("最近剧情"))
    }

    @Test
    fun normalActionUsesChoiceWording() {
        val messages = Prompts.gameActionMessages(
            bible = bible,
            gameState = game,
            stats = StatValues(),
            goal = StoryGoal("找到妹妹"),
            timeline = StoryTimeline("血月", 20, 1),
            choiceId = "choice_1",
            choiceText = "过桥",
            character = character,
            history = emptyList(),
        )
        assertTrue(messages[1].content.contains("玩家选择了: 过桥 (ID: choice_1)"))
    }

    @Test
    fun contextHistoryIsClippedPerEntry() {
        val history = listOf(
            HistoryEntry(HistoryType.CHOICE, "过桥"),
            HistoryEntry(HistoryType.NARRATION, "字".repeat(400)),
        )
        val formatted = Prompts.formatHistory(history, limit = 8, textLimit = 30)
        assertTrue(formatted.contains("玩家选择：过桥"))
        assertTrue(formatted.contains("…"))
        assertTrue(formatted.lines().size == 2)
    }

    @Test
    fun digestCompressesLargeBible() {
        val digest = Prompts.bibleDigest(bible)
        assertEquals("测试小说", digest.optString("novelName"))
        assertEquals("古桥体系", digest.getJSONObject("powerSystem").optString("name"))
        assertEquals("守桥人", digest.getJSONArray("factions").getJSONObject(0).optString("name"))
        assertEquals("桥下有什么？", digest.getJSONArray("mysteries").getString(0))
    }

    @Test
    fun digestCarriesOriginalFlavor() {
        val flavorful = bible.copy(
            tone = "冷峻克制，短句",
            themes = listOf("孤独", "守望"),
            locations = listOf(KeyLocation("古桥", "横跨雾江", "守桥人世代驻守")),
            keyItems = listOf(KeyItem("断剑", "剑身有缺口", "林澈父亲留下的信物")),
            iconicScenes = listOf(IconicScene("雨夜过桥", "林澈第一次看见守桥人", "全书基调")),
            quotes = listOf(ClassicQuote("不要回头。", "守桥人", "送别时")),
        )
        val digest = Prompts.bibleDigest(flavorful)
        assertEquals("冷峻克制，短句", digest.optString("tone"))
        assertEquals("孤独", digest.getJSONArray("themes").getString(0))
        assertEquals("古桥", digest.getJSONArray("locations").getJSONObject(0).optString("name"))
        assertEquals("断剑", digest.getJSONArray("keyItems").getJSONObject(0).optString("name"))
        assertEquals("雨夜过桥", digest.getJSONArray("iconicScenes").getJSONObject(0).optString("title"))
        assertEquals("不要回头。", digest.getJSONArray("quotes").getJSONObject(0).optString("text"))
    }

    @Test
    fun cleanPolishStripsLabelsFencesAndQuotes() {
        assertEquals("我掀开斗笠，看清他的脸。", Prompts.cleanPolish("润色：我掀开斗笠，看清他的脸。"))
        assertEquals("我贴着桥栏后退了一步。", Prompts.cleanPolish("```\n“我贴着桥栏后退了一步。”\n```"))
        assertEquals("", Prompts.cleanPolish("   "))
        assertEquals("短句", Prompts.cleanPolish("润色后：短句"))
    }

    @Test
    fun epiloguePromptCarriesOutcomeAndHistory() {
        val messages = Prompts.epilogueMessages(
            bible = bible,
            character = character,
            goal = StoryGoal("找到妹妹", "在血月前带她离开"),
            timeline = StoryTimeline("血月", 20, 18),
            outcome = "success",
            outcomeReason = "达成最终目标：找到妹妹",
            endingText = "你们在血月升起前离开。",
            history = listOf(HistoryEntry(HistoryType.NARRATION, "你找到了妹妹。")),
        )
        val content = messages[1].content
        assertTrue(content.contains("成功达成最终目标"))
        assertTrue(content.contains("故事停在第 18 / 20 天"))
        assertTrue(content.contains("你找到了妹妹"))
        assertTrue(messages[0].content.contains("成功结局写目标达成后的余波"))
    }

    @Test
    fun goalCheckPromptIncludesSceneAndNarration() {
        val messages = Prompts.goalCheckMessages(
            StoryGoal("找到妹妹", "在血月前带她离开"),
            "你终于在桥下找到了妹妹。",
            game.currentScene,
            emptyList(),
        )
        assertTrue(messages[1].content.contains("最终目标"))
        assertTrue(messages[1].content.contains("你终于在桥下找到了妹妹"))
        assertTrue(messages[1].content.contains("桥心") || messages[1].content.contains("古桥"))
    }

    @Test
    fun fallbackSummaryParsesJsonAndPlainText() {
        assertEquals("测试摘要", Prompts.parseFallbackSummary("""{"summary":"测试摘要"}"""))
        assertEquals("没有 JSON 的摘要", Prompts.parseFallbackSummary("没有 JSON 的摘要"))
    }
}
