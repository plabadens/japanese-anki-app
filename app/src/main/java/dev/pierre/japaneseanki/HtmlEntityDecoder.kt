package dev.pierre.japaneseanki

/** The callers intentionally retain different historical numeric-entity acceptance policies. */
internal enum class NumericEntityPolicy {
    TRANSLATION,
    DUPLICATE_CANONICALIZATION,
}

/** One-pass decoder for the small entity set produced by the existing service/export paths. */
internal object HtmlEntityDecoder {
    private val namedEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00a0",
    )

    fun decode(text: String, policy: NumericEntityPolicy): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] != '&') {
                out.append(text[index++])
                continue
            }

            val semicolon = text.indexOf(';', index + 1)
            if (semicolon < 0 || semicolon - index > MAX_ENTITY_DISTANCE) {
                out.append(text[index++])
                continue
            }

            val entity = text.substring(index + 1, semicolon)
            val decoded = namedEntities[entity] ?: decodeNumericEntity(entity, policy)
            if (decoded == null) {
                out.append(text[index++])
            } else {
                out.append(decoded)
                index = semicolon + 1
            }
        }
        return out.toString()
    }

    private fun decodeNumericEntity(entity: String, policy: NumericEntityPolicy): String? {
        if (!entity.startsWith('#')) return null
        val value = if (entity.startsWith("#x", ignoreCase = true)) {
            entity.substring(2).toIntOrNull(16)
        } else {
            entity.substring(1).toIntOrNull()
        } ?: return null

        if (!Character.isValidCodePoint(value)) return null
        when (policy) {
            NumericEntityPolicy.TRANSLATION -> if (value == 0) return null
            NumericEntityPolicy.DUPLICATE_CANONICALIZATION -> if (value in SURROGATE_RANGE) return null
        }
        return String(Character.toChars(value))
    }

    private const val MAX_ENTITY_DISTANCE = 12
    private val SURROGATE_RANGE = 0xD800..0xDFFF
}