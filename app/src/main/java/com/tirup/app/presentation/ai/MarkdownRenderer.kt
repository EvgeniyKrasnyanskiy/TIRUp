package com.tirup.app.presentation.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object MarkdownRenderer {

    fun parse(text: String, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { lineIdx, rawLine ->
                var line = rawLine
                var isHeader = 0
                var prefix = ""

                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("### ") -> {
                        isHeader = 3
                        line = trimmed.substring(4)
                    }
                    trimmed.startsWith("## ") -> {
                        isHeader = 2
                        line = trimmed.substring(3)
                    }
                    trimmed.startsWith("# ") -> {
                        isHeader = 1
                        line = trimmed.substring(2)
                    }
                    trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                        prefix = "  • "
                        line = trimmed.substring(2)
                    }
                }

                if (prefix.isNotEmpty()) {
                    append(prefix)
                }

                val headerSize = when (isHeader) {
                    1 -> 18.sp
                    2 -> 16.sp
                    3 -> 15.sp
                    else -> null
                }

                parseInlineStyles(line, defaultColor, isHeader > 0, headerSize)

                if (lineIdx < lines.lastIndex) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.parseInlineStyles(
        text: String,
        defaultColor: Color,
        isHeader: Boolean,
        headerSize: TextUnit?
    ) {
        val regex = Regex("""(\*\*\*.*?\*\*\*|\*\*.*?\*\*|\*[^*\n]+?\*|`[^`\n]+?`)""")
        var lastIndex = 0

        val matches = regex.findAll(text)
        for (match in matches) {
            val range = match.range
            if (range.first > lastIndex) {
                val plain = text.substring(lastIndex, range.first)
                appendWithHeader(plain, isHeader, headerSize)
            }

            val token = match.value
            when {
                token.startsWith("***") && token.endsWith("***") && token.length >= 6 -> {
                    val content = token.substring(3, token.length - 3)
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = headerSize ?: TextUnit.Unspecified
                        )
                    )
                    append(content)
                    pop()
                }
                token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                    val content = token.substring(2, token.length - 2)
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = headerSize ?: TextUnit.Unspecified
                        )
                    )
                    append(content)
                    pop()
                }
                token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                    val content = token.substring(1, token.length - 1)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            fontSize = headerSize ?: TextUnit.Unspecified
                        )
                    )
                    append(content)
                    pop()
                }
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val content = token.substring(1, token.length - 1)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = defaultColor.copy(alpha = 0.12f),
                            fontSize = 13.sp
                        )
                    )
                    append(content)
                    pop()
                }
                else -> {
                    appendWithHeader(token, isHeader, headerSize)
                }
            }
            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            val remaining = text.substring(lastIndex)
            appendWithHeader(remaining, isHeader, headerSize)
        }
    }

    private fun AnnotatedString.Builder.appendWithHeader(
        plain: String,
        isHeader: Boolean,
        headerSize: TextUnit?
    ) {
        if (isHeader) {
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = headerSize ?: TextUnit.Unspecified
                )
            )
            append(plain)
            pop()
        } else {
            append(plain)
        }
    }
}
