package com.luxboy.mysecretary.ui.theme

import androidx.compose.ui.graphics.Color

data class EventCategory(
    val displayName: String,
    val color: Color,
)

/**
 * Fixed 6-slot palette indexed by [com.luxboy.mysecretary.domain.model.Event.colorTag].
 * Index 0 is the default (no explicit category).
 */
object EventCategoryPalette {
    // 400-level vibrant tones — readable on both light and dark backgrounds.
    val categories: List<EventCategory> = listOf(
        EventCategory("업무", Color(0xFF60A5FA)),
        EventCategory("개인", Color(0xFF34D399)),
        EventCategory("약속", Color(0xFFF87171)),
        EventCategory("건강", Color(0xFFC084FC)),
        EventCategory("가족", Color(0xFFFBBF24)),
        EventCategory("기타", Color(0xFF9CA3AF)),
    )

    fun colorOf(tag: Int): Color = categories.getOrNull(tag)?.color ?: categories[0].color
    fun nameOf(tag: Int): String = categories.getOrNull(tag)?.displayName ?: categories[0].displayName
}
