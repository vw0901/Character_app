package com.ifitwereyou.character.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: ProjectStore

    @Before
    fun setUp() {
        store = ProjectStore(temp.newFolder("projects"))
    }

    private fun document(novelName: String = "三体"): ProjectDocument =
        createProjectFromSearch(
            SearchData(
                novelName = novelName,
                summary = "摘要",
                results = emptyList(),
            ),
        )

    @Test
    fun savesReadsAndListsProjects() {
        val saved = store.save(document())
        assertEquals("三体", saved.id)
        assertTrue(saved.updatedAt.isNotBlank())

        val loaded = store.read("三体")
        assertNotNull(loaded)
        assertEquals("摘要", loaded!!.search?.summary)
        assertEquals(PROJECT_FORMAT, loaded.format)

        val listed = store.list()
        assertEquals(1, listed.size)
        assertEquals("三体", listed.first().novelName)
        assertTrue(listed.first().hasSearch)
        assertFalse(listed.first().hasAdventure)
    }

    @Test
    fun writesUtf8JsonWithFormatVersion() {
        store.save(document())
        val file = File(store.pathFor("三体").parentFile, "三体.json")
        assertTrue(file.isFile)
        val raw = file.readText(Charsets.UTF_8)
        assertTrue(raw.contains("三体"))
        assertTrue(raw.contains(PROJECT_FORMAT))
        assertTrue(raw.contains("\"formatVersion\": 2"))
    }

    @Test
    fun rejectsUnsafeIdsAndInvalidDocuments() {
        assertThrows(ProjectStoreException::class.java) { store.save(document().copy(id = "a/b")) }
        assertThrows(ProjectStoreException::class.java) { store.save(document().copy(id = "con")) }
        assertThrows(ProjectStoreException::class.java) { store.save(document().copy(id = "x".repeat(65))) }
        assertThrows(ProjectStoreException::class.java) { store.save(document().copy(novelName = " ")) }
        assertThrows(ProjectStoreException::class.java) {
            store.save(document().copy(format = "other/app"))
        }
    }

    @Test
    fun deletesExistingProjectAndIgnoresBrokenFiles() {
        store.save(document("临时"))
        assertTrue(store.delete("临时"))
        assertFalse(store.delete("临时"))
        assertNull(store.read("临时"))

        File(store.pathFor("正常").parentFile, "broken.json").writeText("{not-json", Charsets.UTF_8)
        assertEquals(0, store.list().size)
    }

    @Test
    fun listIsSortedByUpdatedAt() {
        store.save(document("甲"))
        store.save(document("乙"))
        val listed = store.list()
        assertEquals(2, listed.size)
        assertTrue(listed[0].updatedAt >= listed[1].updatedAt)
    }
}
