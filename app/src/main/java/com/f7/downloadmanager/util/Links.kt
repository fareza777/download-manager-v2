package com.f7.downloadmanager.util

object Links {
    private val urlRegex = Regex("(https?://[^\\s\"'<>]+|magnet:\\?[^\\s\"'<>]+)", RegexOption.IGNORE_CASE)

    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return urlRegex.find(raw)?.value?.trimEnd('.', ',', ')', ']')
    }

    fun all(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("magnet:", true) }
    }

    fun looksLikeHls(raw: String?): Boolean = raw?.substringBefore('?')?.lowercase()?.endsWith(".m3u8") == true

    fun searchUrl(query: String): String =
        "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(query, "UTF-8")

    fun normalize(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.contains(' ') || !trimmed.contains('.') -> searchUrl(trimmed)
            else -> "https://$trimmed"
        }
    }
}
