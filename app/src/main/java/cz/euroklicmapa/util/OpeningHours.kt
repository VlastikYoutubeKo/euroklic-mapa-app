package cz.euroklicmapa.util

import java.text.Normalizer
import java.util.Calendar

/**
 * Lenient, **conservative** parser for the free-text station-hours strings the backend carries
 * for ČD stations only (`opening_hours` = station-hall hours, ~86 rows, since 2026-09-09 mostly
 * per-day from Správa železnic with spaces around the dash — `"Po-Ne 04:30 - 23:30"`,
 * `"Po-Pá 04:35 - 20:00 So-Ne 05:00 - 20:00"`; `wc_opening_hours` = WC-specific hours, ~6 rows,
 * still cd.cz-sourced; both `null` everywhere else). Pure JVM — no Android imports — so it is
 * unit-testable and safe on API 24 (`java.time` is not desugared here, so we speak [Calendar]).
 *
 * The contract is deliberately narrow: recognise only the handful of Czech formats we can be
 * confident about and return `null` (or a model whose [OpeningHours.statusAt] yields
 * [OpenState.UNKNOWN]) for anything else. A missing badge is fine; a wrong "Zavřeno" is not.
 *
 * Recognised:
 *  - always-open markers: `nonstop`, `nepřetržitě`, `24 hodin`, `0–24`, `00:00–24:00`,
 *    `denně 0-24`, `24/7`
 *  - a run of `<day-spec> <time>-<time>[ <time>-<time>…]` groups, the groups separated by a
 *    `;` / newline **or just by whitespace before the next day token** — the real feed writes
 *    `"Po-Pá 03:50-21:35 So-Ne 04:50-21:35"` (two clauses, space-separated) and
 *    `"Po,St,Pá 03:50-19:30 Čt 03:50-21:00 So,Ne 04:50-21:00"` (comma day-lists)
 *  - `<day-spec>` = a day range (`Po–Pá`, `Po-Ne`), a comma list (`Po, St, Pá`), a single day,
 *    or `denně` / `každý den` (= Po–Ne); day tokens Po Út/Ut St Čt/Ct Pá/Pa So Ne, case- and
 *    diacritics-insensitive. An unknown / garbled token inside a day list is **skipped** (a
 *    scrape quirk shouldn't sink the whole parse); if a clause ends up with no usable day it
 *    is dropped, and if nothing usable remains the parse is `null`.
 *  - a day-spec-less leading time run applies to every day (`"0:00-1:30 2:30-24:00"`)
 *  - times `H`, `H:MM`, `HH`, `HH:MM`, `HH.MM`; an end `<=` start (or `24:xx`) runs past midnight
 *
 * Explicitly treated as UNKNOWN (not guessed): `od 6:00 do 22:00` phrasing, seasonal /
 * conditional notes ("v létě", "dle vlaků", "o víkendu zavřeno" as prose), any trailing prose
 * the grammar below does not consume.
 */
enum class OpenState { OPEN, CLOSED, UNKNOWN }

class OpeningHours internal constructor(
    private val alwaysOpen: Boolean,
    private val clauses: List<Clause>,
) {
    /** Half-open minute interval since local midnight. [endMin] may exceed 1440 (runs past midnight). */
    internal data class Interval(val startMin: Int, val endMin: Int)

    /** [days] holds internal day indices Mon=0 .. Sun=6. */
    internal data class Clause(val days: Set<Int>, val intervals: List<Interval>)

    fun statusAt(now: Calendar = Calendar.getInstance()): OpenState {
        if (alwaysOpen) return OpenState.OPEN
        if (clauses.isEmpty()) return OpenState.UNKNOWN

        val today = calendarToIndex(now.get(Calendar.DAY_OF_WEEK))
        val yesterday = if (today == 0) 6 else today - 1
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (clause in clauses) {
            for (i in clause.intervals) {
                val eveningEnd = if (i.endMin > 1440) 1440 else i.endMin
                if (today in clause.days && minutes >= i.startMin && minutes < eveningEnd) {
                    return OpenState.OPEN
                }
                if (i.endMin > 1440 && yesterday in clause.days && minutes < i.endMin - 1440) {
                    return OpenState.OPEN
                }
            }
        }
        return OpenState.CLOSED
    }

    /** Calendar SUNDAY(1)..SATURDAY(7) -> internal Mon=0..Sun=6. */
    private fun calendarToIndex(dow: Int): Int = if (dow == Calendar.SUNDAY) 6 else dow - 2
}

private const val TIME = "\\d{1,2}(?:[:.]\\d{2})?"
private const val TIME_RANGE = "$TIME\\s*-\\s*$TIME"

private val TIME_RANGE_REGEX = Regex(TIME_RANGE)
private val TIME_REGEX = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?$")

/** Leftover the grammar is allowed to ignore: whitespace, separators, the words "hod"/"hodin"/"h". */
private val FILLER_REGEX = Regex("(?:hodin|hod|h|[\\s,;:.\\-])+")

private val DAY_INDEX = mapOf(
    "po" to 0, "ut" to 1, "st" to 2, "ct" to 3, "pa" to 4, "so" to 5, "ne" to 6,
)
private val ALL_DAYS = (0..6).toSet()

/** Tokens the day-spec parser silently ignores rather than treating as a garbled day. */
private val DAY_FILLER_TOKENS = setOf("hod", "hodin", "h")

/** Result of reading the text that sits where a clause's day-spec is expected. */
private class DaySpec(val days: Set<Int>, val hadForeignWord: Boolean)

/**
 * Cut off the temporary-change notices some (cd.cz-sourced) rows carry — everything from the
 * first `UPOZORNĚNÍ:` / `Mimořádná změna provozní doby` onward. Those are date-ranged one-off
 * overrides we can't meaningfully render, and left in they wreck both the display and the parse.
 * Returns the cleaned string trimmed, or `null` if nothing usable is left.
 */
fun sanitizeStationHours(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    // Kotlin's lowercase() is Unicode-aware, so this catches "UPOZORNĚNÍ" / "Mimořádná" too
    // (a plain regex `(?i)` in Java is ASCII-only and would miss the accented capitals).
    val lower = raw.lowercase()
    val cutAt = listOf("upozorněn", "upozornen", "mimořádná změna", "mimoradna zmena")
        .mapNotNull { m -> lower.indexOf(m).takeIf { it >= 0 } }
        .minOrNull()
    val cut = (if (cutAt != null) raw.substring(0, cutAt) else raw)
        .trim()
        .trimEnd(',', ';', '-', ' ')
    return cut.ifBlank { null }
}

/**
 * @return an [OpeningHours] model, or `null` when the string is blank or anything about it is
 * ambiguous / unrecognised. Never throws.
 */
fun parseOpeningHours(raw: String?): OpeningHours? {
    val clean = sanitizeStationHours(raw) ?: return null
    val norm = normalize(clean)
    if (norm.isBlank()) return null

    if (isAlwaysOpen(norm)) return OpeningHours(alwaysOpen = true, clauses = emptyList())

    // Every HH:MM-HH:MM range, in order, with its position in `norm`.
    val ranges = TIME_RANGE_REGEX.findAll(norm).toList()
    if (ranges.isEmpty()) return null

    // Group consecutive ranges whose only separation is comma / whitespace — those share one
    // day-spec ("Po-Pá 6-20, 21-23"). A gap that carries a ';' or a letter opens a new clause,
    // which is exactly what splits "Po-Pá 3:50-21:35 So-Ne 4:50-21:35" into two.
    val groups = mutableListOf<MutableList<MatchResult>>()
    for (m in ranges) {
        val prev = groups.lastOrNull()
        if (prev != null) {
            val gap = norm.substring(prev.last().range.last + 1, m.range.first)
            if (gap.all { it == ' ' || it == ',' }) {
                prev += m
                continue
            }
        }
        groups += mutableListOf(m)
    }

    val clauses = mutableListOf<OpeningHours.Clause>()
    var cursor = 0
    for (g in groups) {
        val daySpecText = norm.substring(cursor, g.first().range.first)
        val spec = parseDaySpec(daySpecText)
        val days = when {
            spec.days.isNotEmpty() -> spec.days
            spec.hadForeignWord -> return null // prose where a day-spec was expected
            else -> ALL_DAYS                   // no day-spec at all -> every day
        }

        val intervals = mutableListOf<OpeningHours.Interval>()
        for (r in g) {
            val parts = r.value.split('-', limit = 2).map { it.trim() }
            if (parts.size != 2) return null
            val start = parseMinutes(parts[0]) ?: return null
            var end = parseMinutes(parts[1]) ?: return null
            if (start >= 1440) return null // a start of 24:00 makes no sense
            if (end <= start) end += 1440  // runs past midnight ("4:00-00:30", "22-6")
            intervals += OpeningHours.Interval(start, end)
        }
        if (intervals.isEmpty()) return null

        clauses += OpeningHours.Clause(days, intervals)
        cursor = g.last().range.last + 1
    }

    // Anything after the last time range must be pure filler — trailing prose ("kromě svátků",
    // "jinak dle dohody") still fails the whole parse.
    if (FILLER_REGEX.replace(norm.substring(cursor), "").isNotEmpty()) return null
    if (clauses.isEmpty()) return null

    return OpeningHours(alwaysOpen = false, clauses = clauses)
}

/** Lowercase, strip diacritics, normalise dashes to '-', collapse whitespace. */
private fun normalize(raw: String): String {
    val noDiacritics = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noDiacritics
        .lowercase()
        .replace('–', '-') // en dash
        .replace('—', '-') // em dash
        .replace('−', '-') // minus sign
        .replace(Regex("[ \\t\\r\\n\\u00A0]+"), " ")
        .trim()
}

private fun isAlwaysOpen(norm: String): Boolean {
    if (norm.contains("nonstop") || norm.contains("nepretrzit")) return true
    val s = norm
        .replace("kazdy den", "")
        .replace("denne", "")
        .replace(",", "")
        .replace(";", "")
        .replace(" ", "")
    if (s == "24hodin" || s == "24hod" || s == "24/7") return true
    return Regex("^0{1,2}([:.]00)?-24([:.]00)?$").matches(s)
}

/**
 * Read the text sitting where a clause's day-spec is expected. Day tokens (single, `Po-Pá`
 * ranges, comma lists, `denně`/`každý den`) are collected; punctuation and the `hod`/`h`/`od`
 * filler words are ignored; **any other alphabetic token is treated as garbled** — skipped so
 * a scrape quirk doesn't sink the parse, but flagged via [DaySpec.hadForeignWord] so a
 * day-spec that is *nothing but* prose can still be rejected by the caller.
 */
private fun parseDaySpec(text: String): DaySpec {
    val t = text.trim()
    if (t.isEmpty()) return DaySpec(emptySet(), hadForeignWord = false)

    // "denně" / "každý den" anywhere -> every day.
    var body = t
    var everyDay = false
    for (marker in listOf("kazdy den", "denne")) {
        if (body.contains(marker)) {
            everyDay = true
            body = body.replace(marker, " ")
        }
    }

    val days = mutableSetOf<Int>()
    var hadForeignWord = false
    for (tok in body.split(Regex("[\\s,]+")).filter { it.isNotBlank() }) {
        if (tok in DAY_FILLER_TOKENS) continue
        if (tok.none { it.isLetterOrDigit() }) continue // pure punctuation
        if (tok.contains('-')) {
            val ends = tok.split('-', limit = 2)
            val from: Int? = DAY_INDEX[ends[0].trim()]
            val to: Int? = DAY_INDEX[ends.getOrElse(1) { "" }.trim()]
            if (from != null && to != null) {
                var d: Int = from
                while (true) {
                    days.add(d)
                    if (d == to) break
                    d = (d + 1) % 7
                }
            } else {
                hadForeignWord = true
            }
        } else {
            val idx: Int? = DAY_INDEX[tok]
            if (idx != null) days.add(idx) else hadForeignWord = true
        }
    }
    if (everyDay) days += ALL_DAYS
    return DaySpec(days, hadForeignWord)
}

private fun parseMinutes(t: String): Int? {
    val m = TIME_REGEX.matchEntire(t) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (min > 59) return null
    if (h > 24) return null
    if (h == 24 && min != 0) return null
    return h * 60 + min
}
