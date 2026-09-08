package cz.euroklicmapa.util

import java.text.Normalizer
import java.util.Calendar

/**
 * Lenient, **conservative** parser for the free-text `opening_hours` string that the backend
 * carries for ČD stations only (~109 rows; `null` everywhere else). Pure JVM — no Android
 * imports — so it is unit-testable and safe on API 24 (`java.time` is not desugared here, so
 * we speak [Calendar]).
 *
 * The contract is deliberately narrow: recognise only the handful of Czech formats we can be
 * confident about and return `null` (or a model whose [OpeningHours.statusAt] yields
 * [OpenState.UNKNOWN]) for anything else. A missing badge is fine; a wrong "Zavřeno" is not.
 *
 * Recognised:
 *  - always-open markers: `nonstop`, `nepřetržitě`, `24 hodin`, `0–24`, `00:00–24:00`,
 *    `denně 0-24`, `24/7`
 *  - day-range + time-range clauses: `Po–Pá 6:00–22:00`, `Po-Pá 5:30-23:00`,
 *    `Po–Ne 4:00–00:30`, `Po, St, Pá 8–16`
 *  - several clauses separated by `,` `;` or newlines: `Po–Pá 6:00–20:00; So–Ne 8:00–18:00`
 *  - day tokens Po Út/Ut St Čt/Ct Pá/Pa So Ne (case- and diacritics-insensitive), plus
 *    `denně` / `každý den` = Po–Ne
 *  - times `H`, `H:MM`, `HH`, `HH:MM`, `HH.MM`; an end `<=` start (or `00:xx`) runs past midnight
 *
 * Explicitly treated as UNKNOWN (not guessed): `od 6:00 do 22:00` phrasing, seasonal /
 * conditional notes ("v létě", "dle vlaků", "o víkendu zavřeno" as prose), anything with a
 * leftover word the grammar below does not consume.
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

private const val DAY = "(?:po|ut|st|ct|pa|so|ne|denne|kazdy den)"
private const val DAY_RANGE = "$DAY\\s*-\\s*$DAY"
private const val DAY_PART = "(?:$DAY_RANGE|$DAY)(?:\\s*,\\s*(?:$DAY_RANGE|$DAY))*"
private const val TIME = "\\d{1,2}(?:[:.]\\d{2})?"
private const val TIME_RANGE = "$TIME\\s*-\\s*$TIME"
private const val TIME_PART = "$TIME_RANGE(?:\\s*,\\s*$TIME_RANGE)*"

private val CLAUSE_REGEX = Regex("($DAY_PART)?\\s*($TIME_PART)")
private val DAY_TOKEN_REGEX = Regex("$DAY_RANGE|$DAY")
private val TIME_RANGE_REGEX = Regex(TIME_RANGE)
private val TIME_REGEX = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?$")

/** Leftover the grammar is allowed to ignore: whitespace, separators, the words "hod"/"hodin"/"h". */
private val FILLER_REGEX = Regex("(?:hodin|hod|h|[\\s,;:.\\-])+")

private val DAY_INDEX = mapOf(
    "po" to 0, "ut" to 1, "st" to 2, "ct" to 3, "pa" to 4, "so" to 5, "ne" to 6,
)
private val ALL_DAYS = (0..6).toSet()

/**
 * @return an [OpeningHours] model, or `null` when the string is blank or anything about it is
 * ambiguous / unrecognised. Never throws.
 */
fun parseOpeningHours(raw: String?): OpeningHours? {
    if (raw.isNullOrBlank()) return null
    val norm = normalize(raw)
    if (norm.isBlank()) return null

    if (isAlwaysOpen(norm)) return OpeningHours(alwaysOpen = true, clauses = emptyList())

    val clauses = mutableListOf<OpeningHours.Clause>()
    val leftover = StringBuilder(norm)
    var matchedAny = false

    for (m in CLAUSE_REGEX.findAll(norm)) {
        val dayPart = m.groupValues[1].trim()
        val timePart = m.groupValues[2].trim()

        val days = if (dayPart.isEmpty()) ALL_DAYS else parseDayPart(dayPart) ?: return null
        if (days.isEmpty()) return null

        val intervals = parseTimePart(timePart) ?: return null
        if (intervals.isEmpty()) return null

        clauses += OpeningHours.Clause(days, intervals)
        matchedAny = true
        // Blank out this match so the coverage check below only sees filler.
        for (idx in m.range) leftover.setCharAt(idx, ' ')
    }

    if (!matchedAny) return null
    if (FILLER_REGEX.replace(leftover.toString(), "").isNotEmpty()) return null

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

/** `"po-pa, ne"` -> set of internal day indices, or `null` if any token is unrecognised. */
private fun parseDayPart(dayPart: String): Set<Int>? {
    val result = mutableSetOf<Int>()
    for (token in DAY_TOKEN_REGEX.findAll(dayPart).map { it.value.trim() }) {
        if (token == "denne" || token == "kazdy den") {
            result += ALL_DAYS
            continue
        }
        if (token.contains('-')) {
            val (a, b) = token.split('-', limit = 2).map { it.trim() }
            val from = DAY_INDEX[a] ?: return null
            val to = DAY_INDEX[b] ?: return null
            var d = from
            while (true) {
                result += d
                if (d == to) break
                d = (d + 1) % 7
            }
        } else {
            result += DAY_INDEX[token] ?: return null
        }
    }
    return if (result.isEmpty()) null else result
}

/** `"6:00-20:00, 21:00-23:00"` -> intervals, or `null` if any range is malformed / ambiguous. */
private fun parseTimePart(timePart: String): List<OpeningHours.Interval>? {
    val out = mutableListOf<OpeningHours.Interval>()
    for (range in TIME_RANGE_REGEX.findAll(timePart).map { it.value }) {
        val parts = range.split('-', limit = 2).map { it.trim() }
        if (parts.size != 2) return null
        val start = parseMinutes(parts[0]) ?: return null
        var end = parseMinutes(parts[1]) ?: return null
        if (start >= 1440) return null // a start of 24:00 makes no sense
        if (end <= start) end += 1440  // runs past midnight ("4:00-00:30", "22-6")
        out += OpeningHours.Interval(start, end)
    }
    return if (out.isEmpty()) null else out
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
