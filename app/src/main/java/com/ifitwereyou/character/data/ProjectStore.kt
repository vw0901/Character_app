package com.ifitwereyou.character.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProjectStoreException(message: String) : IllegalArgumentException(message)

/**
 * 项目存档的本地文件持久化。
 *
 * 每个项目保存为 `files/projects/<id>.json` 一个文件，包含检索结果、设定圣经与冒险进度。
 * 写入使用临时文件替换，并限制 id 只能是安全字符。
 * 文件格式与原项目后端一致，因此两边的存档可以互相导入。
 */
class ProjectStore(private val root: File) {

    companion object {
        const val MAX_PROJECT_BYTES = 4 * 1024 * 1024
        const val MAX_NOVEL_NAME_LENGTH = 200
        private val SAFE_ID_REGEX = Regex("^[\\p{L}\\p{N}_-]{1,64}$")
        private val WINDOWS_RESERVED = buildSet {
            addAll(listOf("con", "prn", "aux", "nul"))
            for (index in 1..9) {
                add("com$index")
                add("lpt$index")
            }
        }

        fun forContext(context: Context): ProjectStore =
            ProjectStore(File(context.filesDir, "projects"))
    }

    fun sanitizeId(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) throw ProjectStoreException("缺少项目 ID")
        if (!SAFE_ID_REGEX.matches(value)) {
            throw ProjectStoreException("项目 ID 只能包含文字、数字、下划线和短横线（1-64 位）")
        }
        if (value.lowercase() in WINDOWS_RESERVED) {
            throw ProjectStoreException("项目 ID 是系统保留名称，请换一个")
        }
        return value
    }

    fun pathFor(rawId: String): File {
        val id = sanitizeId(rawId)
        val directory = root.canonicalFile
        val path = File(directory, "$id.json").canonicalFile
        if (path.parentFile != directory) throw ProjectStoreException("项目 ID 非法")
        return path
    }

    fun list(): List<ProjectSummary> {
        if (!root.isDirectory) return emptyList()
        val summaries = mutableListOf<ProjectSummary>()
        val files = root.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
            ?: return emptyList()
        for (file in files) {
            try {
                if (file.length() > MAX_PROJECT_BYTES) continue
                val raw = file.readText(StandardCharsets.UTF_8)
                val record = JSONObject(raw)
                val name = record.opt("novelName") as? String ?: file.nameWithoutExtension
                summaries.add(
                    ProjectSummary(
                        id = file.nameWithoutExtension,
                        novelName = name,
                        updatedAt = record.opt("updatedAt") as? String ?: "",
                        appVersion = record.opt("appVersion") as? String ?: "",
                        hasSearch = record.opt("search") != null,
                        hasBible = record.opt("bible") != null,
                        hasAdventure = hasAdventure(record),
                    ),
                )
            } catch (_: Exception) {
                // 单个损坏的存档不应让整个书架打不开。
            }
        }
        return summaries.sortedByDescending { it.updatedAt }
    }

    private fun hasAdventure(record: JSONObject): Boolean {
        val adventures = record.opt("adventures")
        if (adventures is JSONObject) {
            val keys = adventures.keys()
            while (keys.hasNext()) {
                val book = adventures.opt(keys.next())
                if (book is JSONObject && book.opt("gameState") != null) return true
            }
        }
        val legacy = record.opt("adventure")
        return legacy is JSONObject && legacy.opt("gameState") != null
    }

    fun read(rawId: String): ProjectDocument? {
        val path = pathFor(rawId)
        if (!path.isFile || path.length() > MAX_PROJECT_BYTES) return null
        return try {
            val raw = path.readText(StandardCharsets.UTF_8)
            normalizeProject(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    /** 返回实际写入的文档（id、格式版本与更新时间已被服务器端规范化）。 */
    fun save(document: ProjectDocument): ProjectDocument {
        if (document.format.isNotEmpty() && document.format != PROJECT_FORMAT) {
            throw ProjectStoreException("无法识别的存档格式")
        }
        val novelName = document.novelName.trim()
        if (novelName.isEmpty()) throw ProjectStoreException("项目存档缺少小说名称")
        if (novelName.length > MAX_NOVEL_NAME_LENGTH) throw ProjectStoreException("小说名称过长")

        val id = sanitizeId(document.id)
        val timestamp = nowIso()
        val normalized = document.copy(
            format = PROJECT_FORMAT,
            formatVersion = PROJECT_FORMAT_VERSION,
            novelName = novelName,
            id = id,
            createdAt = document.createdAt.ifBlank { timestamp },
            updatedAt = timestamp,
        )
        val encoded = ProjectCodec.encodeProject(normalized, pretty = true) + "\n"
        val bytes = encoded.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_PROJECT_BYTES) throw ProjectStoreException("项目存档过大（上限 4 MB）")

        root.mkdirs()
        val target = pathFor(id)
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        try {
            tmp.writeText(encoded, StandardCharsets.UTF_8)
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return normalized
    }

    fun delete(rawId: String): Boolean {
        val path = pathFor(rawId)
        return path.isFile && path.delete()
    }
}
