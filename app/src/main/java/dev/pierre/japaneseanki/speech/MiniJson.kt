package dev.pierre.japaneseanki.speech

/**
 * Small JSON reader for the few Google Cloud payloads this app parses.
 *
 * Android unit tests cannot use org.json (the JVM android.jar stubs throw), and pulling a JSON
 * library in for two response shapes is not worth the dependency. This parser only needs to
 * produce Map/List/String/Double/Boolean/null for well-formed payloads and fail closed otherwise.
 */
internal object MiniJson {
    fun parse(text: String): Any? = Reader(text).let { reader ->
        val value = reader.value()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw IllegalArgumentException("Trailing JSON data")
        value
    }

    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char.code < 0x20 -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private class Reader(private val input: String) {
        private var position = 0

        fun atEnd(): Boolean = position >= input.length

        fun skipWhitespace() {
            while (position < input.length && input[position] in " \t\r\n") position++
        }

        fun value(): Any? {
            skipWhitespace()
            if (position >= input.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (input[position]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> numberValue()
            }
        }

        private fun objectValue(): Map<String, Any?> {
            position++ // {
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek('}')) { position++; return result }
            while (true) {
                skipWhitespace()
                val key = stringValue()
                skipWhitespace()
                if (!peek(':')) throw IllegalArgumentException("Expected ':'")
                position++
                result[key] = value()
                skipWhitespace()
                when {
                    peek(',') -> position++
                    peek('}') -> { position++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or '}'")
                }
            }
        }

        private fun arrayValue(): List<Any?> {
            position++ // [
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek(']')) { position++; return result }
            while (true) {
                result += value()
                skipWhitespace()
                when {
                    peek(',') -> position++
                    peek(']') -> { position++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or ']'")
                }
            }
        }

        private fun stringValue(): String {
            if (!peek('"')) throw IllegalArgumentException("Expected string")
            position++
            val out = StringBuilder()
            while (position < input.length) {
                val char = input[position++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (position >= input.length) break
                        when (val escape = input[position++]) {
                            '"', '\\', '/' -> out.append(escape)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 > input.length) break
                                val code = input.substring(position, position + 4).toIntOrNull(16)
                                    ?: break
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape")
                        }
                    }
                    else -> {
                        if (char.code < 0x20) throw IllegalArgumentException("Control character in string")
                        out.append(char)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun numberValue(): Double {
            val start = position
            if (peek('-')) position++
            while (position < input.length && (input[position].isDigit() || input[position] in ".eE+-")) position++
            if (start == position) throw IllegalArgumentException("Unexpected JSON token")
            return input.substring(start, position).toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!input.startsWith(word, position)) throw IllegalArgumentException("Invalid literal")
            position += word.length
            return value
        }

        private fun peek(char: Char): Boolean = position < input.length && input[position] == char
    }
}
