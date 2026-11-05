package com.maxproductivity.app.util

object CommitmentPhrase {
    const val DISPLAY = "I will not waste my time and potential."

    fun matches(input: String): Boolean = normalize(input) == normalize(DISPLAY)

    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        var lastWasSpace = false
        for (char in text.lowercase()) {
            if (char.isLetterOrDigit()) {
                builder.append(char)
                lastWasSpace = false
            } else {
                // Whitespace and punctuation both act as word separators.
                if (!lastWasSpace && builder.isNotEmpty()) {
                    builder.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return builder.toString().trim()
    }
}
