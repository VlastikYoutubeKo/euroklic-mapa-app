package cz.euroklicmapa.util

import android.content.Context

/** One released version from `CHANGELOG.md` / `CHANGELOG.cs.md` (bundled as assets; same text as the GitHub Release). */
data class ChangelogEntry(val version: String, val date: String?, val items: List<String>) {
    val title: String get() = if (date.isNullOrBlank()) t("Verze %s", version) else t("Verze %s · %s", version, date)
}

object Changelog {
    /**
     * Pure parser. Heading `## 1.2.0 — 2026-11-19`, items are `- ` (or `* `) bullets; entries
     * without items are dropped. A bullet tagged `[iOS]` is hidden here (and `[Android]` is kept
     * with the tag removed); untagged bullets are shown on every platform.
     */
    fun parse(text: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var date: String? = null
        var items = mutableListOf<String>()
        fun flush() {
            version?.let { entries += ChangelogEntry(it, date, items.toList()) }
            items = mutableListOf()
        }
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.startsWith("## ")) {
                flush()
                val heading = line.removePrefix("## ").trim()
                val parts = heading.split(Regex("\\s+"), limit = 2)
                version = parts.firstOrNull()?.takeIf { it.isNotEmpty() }
                var rest = parts.getOrNull(1)?.trim().orEmpty()
                for (dash in listOf("—", "–", "-")) {
                    if (rest.startsWith(dash)) {
                        rest = rest.removePrefix(dash).trim()
                        break
                    }
                }
                date = rest.ifEmpty { null }
            } else if (version != null && (line.startsWith("- ") || line.startsWith("* "))) {
                val item = line.substring(2).trim()
                when {
                    item.startsWith("[iOS]") -> Unit
                    item.startsWith("[Android]") -> items += item.removePrefix("[Android]").trim()
                    else -> items += item
                }
            }
        }
        flush()
        return entries.filter { it.items.isNotEmpty() }
    }

    /** Czech UI gets `CHANGELOG.cs.md`, everything else the English `CHANGELOG.md`. */
    fun load(context: Context): List<ChangelogEntry> {
        val names = if (isCzechUi()) listOf("CHANGELOG.cs.md", "CHANGELOG.md") else listOf("CHANGELOG.md")
        for (name in names) {
            val text = runCatching { context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            if (text != null) return parse(text)
        }
        return emptyList()
    }

    /** "1.0" and "1.0.0" are the same release. */
    fun normalized(version: String): String {
        val parts = version.split('.').toMutableList()
        while (parts.size > 1 && parts.last() == "0") parts.removeAt(parts.lastIndex)
        return parts.joinToString(".")
    }

    fun entryFor(version: String, entries: List<ChangelogEntry>): ChangelogEntry? =
        entries.firstOrNull { normalized(it.version) == normalized(version) }
}
