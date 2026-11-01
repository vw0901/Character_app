package com.ifitwereyou.character.data

import com.ifitwereyou.character.ai.SettingsStore
import com.ifitwereyou.character.game.characterKey
import com.ifitwereyou.character.game.createAdventure
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodecTest {

    @Test
    fun parsesJsonWithFenceTextAndTrailingComma() {
        val raw = """
            这是模型输出的说明文字。
            ```json
            {
              "novelName": "测试小说",
              "characters": [{"name": "林澈", "abilities": "剑术"},],
            }
            ```
        """.trimIndent()
        val parsed = parseJsonLoose(raw) as JSONObject
        assertEquals("测试小说", parsed.optString("novelName"))
        val bible = normalizeBible(parsed)
        assertNotNull(bible)
        assertEquals("林澈", bible!!.characters.first().name)
    }

    @Test
    fun parsesSnakeCaseFields() {
        val bible = normalizeBible(
            JSONObject(
                """{"novelName":"测试","power_system":{"name":"体系","levels":["一阶"]},
                   "factions":[{"name":"势力","key_members":["甲"]}],
                   "characters":[{"name":"林澈","role":"主角"}]}""",
            ),
        )
        assertNotNull(bible)
        assertEquals("体系", bible!!.powerSystem.name)
        assertEquals(listOf("甲"), bible.factions.first().keyMembers)
    }

    @Test
    fun normalizesCheckAndStrengths() {
        val check = normalizeCheck(
            JSONObject(
                """{"overallScore":82,"summary":"整体稳定","issues":[{"title":"漏洞","severity":"中"}],
                   "strengths":["动机清晰"]}""",
            ),
        )
        assertNotNull(check)
        assertEquals(82, check!!.overallScore)
        assertEquals("漏洞", check.issues.first().title)
        assertEquals(listOf("动机清晰"), check.strengths)
    }

    @Test
    fun openingTimelineIsClampedAndStatsHealed() {
        val opening = normalizeGameOpening(
            JSONObject(
                """{
                  "title":"测试冒险","intro":"开场",
                  "finalGoal":{"title":"找到妹妹","description":"在血月前带她离开"},
                  "timeline":{"label":"血月","totalDays":3,"currentDay":9},
                  "playerCharacter":{"name":"林澈","background":"游侠",
                    "initialStats":{"health":5,"power":150,"reputation":10}},
                  "currentScene":{"id":"scene_1","location":"桥","description":"雨",
                    "choices":[{"id":"c1","text":"过桥"}]}
                }""",
            ),
        )
        assertNotNull(opening)
        assertEquals(15, opening!!.timeline.totalDays)
        assertEquals(1, opening.timeline.currentDay)
        assertEquals(20, opening.gameState.playerCharacter.initialStats.health)
        assertEquals(100, opening.gameState.playerCharacter.initialStats.power)
    }

    @Test
    fun openingWithoutGoalOrChoicesIsRejected() {
        val base = """
            {"title":"测试","intro":"开场",
             "timeline":{"totalDays":20,"currentDay":1},
             "playerCharacter":{"name":"林澈","initialStats":{"health":100,"power":50,"reputation":50}},
             "currentScene":{"id":"scene_1","location":"桥","description":"雨",
               "choices":[{"id":"c1","text":"过桥"}]}}
        """.trimIndent()
        val noGoal = parseJsonLoose(base) as JSONObject
        assertNull(normalizeGameOpening(noGoal))

        val noChoices = parseJsonLoose(
            """
            {"title":"测试","intro":"开场",
             "finalGoal":{"title":"找到妹妹"},
             "timeline":{"totalDays":20,"currentDay":1},
             "playerCharacter":{"name":"林澈","initialStats":{"health":100,"power":50,"reputation":50}},
             "currentScene":{"id":"scene_1","location":"桥","description":"雨","choices":[]}}
            """.trimIndent(),
        ) as JSONObject
        assertNull(normalizeGameOpening(noChoices))
    }

    @Test
    fun sceneResultRejectsDeadEndsAndAcceptsEndingWithoutScene() {
        val deadEnd = parseJsonLoose(
            """{"narration":"你走上桥","currentScene":{"id":"s2","location":"桥心",
                "description":"雾","choices":[]},"daysPassed":1}""",
        ) as JSONObject
        assertNull(normalizeSceneResult(deadEnd))

        val ending = parseJsonLoose(
            """{"narration":"你终于找到了妹妹。","isEnding":true,"endingType":"success",
                "goalAchieved":true,"daysPassed":1}""",
        ) as JSONObject
        val result = normalizeSceneResult(ending)
        assertNotNull(result)
        assertTrue(result!!.isEnding)
        assertTrue(result.goalAchieved)
        assertEquals(EndingType.SUCCESS, result.endingType)
    }

    @Test
    fun legacyAdventureIsMigratedIntoBooks() {
        val project = normalizeProject(
            JSONObject(
                """{
                  "novelName":"旧存档",
                  "adventure":{
                    "character":{"mode":"custom","name":"云中客","background":"旅人"},
                    "gameState":{"title":"测试","intro":"开场",
                      "playerCharacter":{"name":"云中客","initialStats":{"health":100,"power":50,"reputation":50}},
                      "currentScene":{"id":"s1","location":"桥","description":"雨","choices":[{"id":"c1","text":"过桥"}]}},
                    "stats":{"health":90,"power":50,"reputation":50},
                    "history":[{"type":"narration","text":"开场"}],
                    "isEnding":false,"turn":1,
                    "goal":{"title":"找到妹妹"},
                    "timeline":{"label":"血月","totalDays":20,"currentDay":2},
                    "outcome":"playing"
                  }
                }""",
            ),
        )
        assertNotNull(project)
        assertEquals("custom:云中客", project!!.activeAdventureKey)
        assertEquals("找到妹妹", project.adventures.getValue("custom:云中客").goal.title)
    }

    @Test
    fun projectExportImportRoundtrip() {
        val search = SearchData(
            novelName = "三体",
            summary = "一个测试摘要",
            results = listOf(SearchResult(title = "条目", url = "https://example.com", snippet = "片段", siteName = "example.com")),
        )
        val original = createProjectFromSearch(search)
        val exported = ProjectCodec.encodeProject(original)
        val imported = parseImportedJson(exported)
        assertEquals(ImportKind.PROJECT, imported.kind)
        assertEquals("三体", imported.project.novelName)
        assertEquals("一个测试摘要", imported.project.search?.summary)
        assertTrue(exported.contains(PROJECT_FORMAT))
    }

    @Test
    fun bibleAndSearchStructuresCanBeImported() {
        val bible = parseImportedJson(
            """{"novelName":"测试","worldview":{"summary":"世界观"},
                "characters":[{"name":"林澈"}]}""",
        )
        assertEquals(ImportKind.BIBLE, bible.kind)
        assertEquals("测试", bible.project.novelName)

        val search = parseImportedJson(
            """{"novelName":"测试","summary":"摘要","results":[{"title":"条目","snippet":"片段"}]}""",
        )
        assertEquals(ImportKind.SEARCH, search.kind)
        assertEquals("摘要", search.project.search?.summary)
    }

    @Test
    fun projectFileNameAvoidsIllegalCharacters() {
        val document = createProjectFromSearch(SearchData(novelName = "三体/黑暗森林"))
            .copy(updatedAt = "2026-11-02T00:00:00Z")
        assertEquals("三体-黑暗森林-2026-11-02.json", projectFileName(document))
    }

    @Test
    fun characterKeysDistinguishCustomAndBibleCharacters() {
        assertEquals(
            "bible:林澈",
            characterKey(CharacterChoice(mode = CharacterMode.EXISTING, name = "林澈")),
        )
        assertEquals(
            "custom:云中客",
            characterKey(CharacterChoice(mode = CharacterMode.CUSTOM, name = " 云中客 ")),
        )
    }

    @Test
    fun adventureReconciliationFixesPlayingDeadSave() {
        val adventure = normalizeAdventure(
            JSONObject(
                """{"character":{"mode":"existing","name":"林澈","background":"游侠"},
                    "stats":{"health":0,"power":50,"reputation":50},
                    "history":[],"isEnding":false,"turn":2,
                    "goal":{"title":"找到妹妹"},
                    "timeline":{"label":"血月","totalDays":20,"currentDay":3},
                    "outcome":"playing"}""",
            ),
        )
        assertNotNull(adventure)
        assertEquals(AdventureOutcome.FAILURE, adventure!!.outcome)
        assertTrue(adventure.isEnding)
    }

    @Test
    fun newAdventureStartsPendingAndPlaying() {
        val adventure = createAdventure(CharacterChoice(mode = CharacterMode.CUSTOM, name = "旅人"))
        assertEquals(AdventureOutcome.PLAYING, adventure.outcome)
        assertTrue(adventure.startedAt.isNotBlank())
        assertEquals("等待命运揭晓", adventure.goal.title)
        assertNull(adventure.gameState)
    }

    @Test
    fun savedSettingsMustBeComplete() {
        assertNull(SettingsStore.parseSavedConfig("""{"baseUrl":"http://x","model":""}"""))
        val config = SettingsStore.parseSavedConfig(
            """{"baseUrl":"http://x","apiKey":"k","model":"m","searchProvider":"tavily","tavilyApiKey":"t"}""",
        )
        assertNotNull(config)
        assertEquals("m", config!!.model)
        assertEquals("tavily", config.searchProvider)
    }
}
