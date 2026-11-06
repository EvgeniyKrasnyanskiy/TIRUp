package com.tirup.app.presentation.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun parse_convertsBoldAndItalicAndBullets() {
        val input = """
### Заголовок
* **Углеводы**: 45 г
* *Примечание*: `тест`
        """.trimIndent()

        val result = MarkdownRenderer.parse(input, Color.Black)
        val text = result.text

        assertTrue(text.contains("Заголовок"))
        assertTrue(text.contains("  • Углеводы: 45 г"))
        assertTrue(text.contains("  • Примечание: тест"))

        // Verify span styles
        val spanStyles = result.spanStyles
        assertNotNull(spanStyles)
        assertTrue(spanStyles.isNotEmpty())

        val boldSpan = spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertNotNull(boldSpan)

        val italicSpan = spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertNotNull(italicSpan)
    }

    @Test
    fun parse_handlesEmptyAndPlainText() {
        val emptyResult = MarkdownRenderer.parse("", Color.Black)
        assertEquals("", emptyResult.text)

        val plainResult = MarkdownRenderer.parse("Обычный текст без разметки", Color.Black)
        assertEquals("Обычный текст без разметки", plainResult.text)
        assertTrue(plainResult.spanStyles.isEmpty())
    }
}
