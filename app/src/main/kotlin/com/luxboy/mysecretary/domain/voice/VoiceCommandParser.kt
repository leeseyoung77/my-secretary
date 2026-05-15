package com.luxboy.mysecretary.domain.voice

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Minimal Korean date/time/intent parser for voice commands.
 *
 * Supports common patterns:
 * - 날짜: 오늘, 내일, 모레, 어제, 이번주/다음주/지난주 X요일, X월 Y일, X요일
 * - 시간: 오전/오후 X시 Y분 / X시 반 / 정오 / 자정 / 아침 / 점심 / 저녁 / 밤
 * - 의도: "삭제/지워/지워줘/지우기" 포함 시 DELETE, 그 외 ADD
 */
object VoiceCommandParser {

    private val DELETE_KEYWORDS = listOf("삭제", "지워", "지우기", "지운다")
    private val ADD_KEYWORDS = listOf("추가", "등록", "잡아", "잡아줘", "넣어", "넣어줘")
    // "있어"/"있어요"는 평서문 어미로도 자주 쓰여서 QUERY 분류에서 제외, 제목 정리에만 사용
    private val QUERY_KEYWORDS = listOf(
        "알려줘", "알려", "보여줘", "조회", "확인",
        "있나", "있나요", "뭐야", "뭐예요", "있는지", "무슨",
    )
    private val CALL_KEYWORDS = listOf(
        "전화", "통화", "걸어줘", "걸어", "통화해", "전화해",
    )
    // 의도 분류엔 영향 없고 제목에서만 제거되는 어미·필러
    private val FILLER_KEYWORDS = listOf(
        "있어요", "있어", "있다", "이다", "이야", "잡혔어", "잡았어",
        "깨워줘", "깨워주세요", "깨워",
    )

    private val KOREAN_NUMERALS = mapOf(
        "한" to 1, "두" to 2, "세" to 3, "네" to 4,
        "다섯" to 5, "여섯" to 6, "일곱" to 7,
        "여덟" to 8, "아홉" to 9, "열" to 10, "십" to 10,
    )

    private val RELATIVE_TIME_REGEX = Regex(
        """(\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|십)\s*(시간|분)(\s*반)?\s*(뒤|후)"""
    )

    private val DOW_MAP = mapOf(
        "월" to DayOfWeek.MONDAY,
        "화" to DayOfWeek.TUESDAY,
        "수" to DayOfWeek.WEDNESDAY,
        "목" to DayOfWeek.THURSDAY,
        "금" to DayOfWeek.FRIDAY,
        "토" to DayOfWeek.SATURDAY,
        "일" to DayOfWeek.SUNDAY,
    )

    private val MONTH_DAY_REGEX = Regex("""(\d+)\s*월\s*(\d+)\s*일""")
    private val NEXT_WEEK_DOW_REGEX = Regex("""다음\s*주\s*([월화수목금토일])\s*요일""")
    private val LAST_WEEK_DOW_REGEX = Regex("""지난\s*주\s*([월화수목금토일])\s*요일""")
    private val THIS_WEEK_DOW_REGEX = Regex("""(?:이번\s*주\s*)?([월화수목금토일])\s*요일""")
    private val TIME_REGEX = Regex("""(오전|오후)?\s*(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?(\s*반)?""")

    fun parse(
        text: String,
        today: LocalDate = LocalDate.now(),
    ): VoiceParseResult {
        val cleaned = text.trim()
        val relativeDuration = parseRelativeDuration(cleaned)
        val intent = when {
            CALL_KEYWORDS.any { it in cleaned } -> VoiceIntent.CALL
            DELETE_KEYWORDS.any { it in cleaned } -> VoiceIntent.DELETE
            // 상대 시간이 있으면 알람 의도이므로 QUERY 키워드보다 우선 ADD
            relativeDuration != null -> VoiceIntent.ADD
            QUERY_KEYWORDS.any { it in cleaned } -> VoiceIntent.QUERY
            else -> VoiceIntent.ADD
        }
        val date = if (relativeDuration != null) today else parseDate(cleaned, today)
        val time = if (relativeDuration != null) null else parseTime(cleaned)
        val title = parseTitle(cleaned)
        return VoiceParseResult(
            intent = intent,
            title = title,
            date = date,
            time = time,
            rawText = cleaned,
            relativeFromNow = relativeDuration,
        )
    }

    private fun parseRelativeDuration(text: String): Duration? {
        val match = RELATIVE_TIME_REGEX.find(text) ?: return null
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val hasHalf = match.groupValues[3].isNotBlank()
        val number = numStr.toIntOrNull() ?: KOREAN_NUMERALS[numStr] ?: return null
        val baseMinutes = if (unit == "시간") number * 60 else number
        val totalMinutes = baseMinutes + if (hasHalf) 30 else 0
        if (totalMinutes <= 0) return null
        return Duration.ofMinutes(totalMinutes.toLong())
    }

    private fun parseDate(text: String, today: LocalDate): LocalDate {
        when {
            "모레" in text -> return today.plusDays(2)
            "내일" in text -> return today.plusDays(1)
            "어제" in text -> return today.minusDays(1)
            "오늘" in text -> return today
        }
        MONTH_DAY_REGEX.find(text)?.let { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            return runCatching { LocalDate.of(today.year, month, day) }
                .getOrNull()
                ?.let { if (it.isBefore(today)) it.plusYears(1) else it }
                ?: today
        }
        NEXT_WEEK_DOW_REGEX.find(text)?.let { m ->
            val dow = DOW_MAP[m.groupValues[1]] ?: return today
            return today.with(dow).let { if (!it.isAfter(today)) it.plusWeeks(1) else it.plusWeeks(1) }
        }
        LAST_WEEK_DOW_REGEX.find(text)?.let { m ->
            val dow = DOW_MAP[m.groupValues[1]] ?: return today
            return today.with(dow).minusWeeks(1)
        }
        THIS_WEEK_DOW_REGEX.find(text)?.let { m ->
            val dow = DOW_MAP[m.groupValues[1]] ?: return today
            val thisWeek = today.with(dow)
            return if (thisWeek.isBefore(today)) thisWeek.plusWeeks(1) else thisWeek
        }
        return today
    }

    private fun parseTime(text: String): LocalTime? {
        if ("정오" in text) return LocalTime.of(12, 0)
        if ("자정" in text) return LocalTime.of(0, 0)

        TIME_REGEX.find(text)?.let { m ->
            val ampm = m.groupValues[1]
            val rawHour = m.groupValues[2].toIntOrNull() ?: return null
            val rawMin = m.groupValues[3].toIntOrNull() ?: 0
            val hasHalf = m.groupValues[4].isNotBlank()
            val minute = if (hasHalf && rawMin == 0) 30 else rawMin

            val hour = when {
                ampm == "오전" -> rawHour % 12
                ampm == "오후" -> if (rawHour < 12) rawHour + 12 else rawHour
                rawHour in 1..7 -> rawHour + 12 // 1~7시는 보통 오후
                rawHour == 12 -> 12
                else -> rawHour
            }
            return runCatching {
                LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            }.getOrNull()
        }

        return when {
            "아침" in text -> LocalTime.of(8, 0)
            "점심" in text -> LocalTime.of(12, 0)
            "저녁" in text -> LocalTime.of(18, 0)
            "밤" in text -> LocalTime.of(21, 0)
            else -> null
        }
    }

    private fun parseTitle(text: String): String {
        var result = " $text "
        val removers = listOf(
            // 상대 시간 패턴은 제일 먼저 제거 (절대 시간 정규식과 충돌 방지)
            RELATIVE_TIME_REGEX,
            Regex("""(다음|지난|이번)\s*주\s*[월화수목금토일]\s*요일"""),
            Regex("""[월화수목금토일]\s*요일"""),
            Regex("""\d+\s*월\s*\d+\s*일"""),
            Regex("""오늘|내일|모레|어제"""),
            Regex("""(오전|오후)?\s*\d{1,2}\s*시(\s*\d{1,2}\s*분)?(\s*반)?"""),
            Regex("""정오|자정|아침|점심|저녁|밤"""),
            Regex(DELETE_KEYWORDS.joinToString("|")),
            Regex(ADD_KEYWORDS.joinToString("|")),
            Regex(QUERY_KEYWORDS.joinToString("|")),
            Regex(CALL_KEYWORDS.joinToString("|")),
            Regex(FILLER_KEYWORDS.joinToString("|")),
            Regex("""\b일정\b"""),
            Regex("""에게|한테|에"""),
        )
        removers.forEach { rx -> result = rx.replace(result, " ") }
        result = result.replace(Regex("""\s+"""), " ").trim()
        // 마지막 조사 정리 (을/를/이/가/은/는/에/에서)
        result = result.replace(Regex("""([가-힣]+?)(을|를|이|가|은|는|에서|에)$"""), "$1")
        return result
    }
}
