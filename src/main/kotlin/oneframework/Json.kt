package oneframework

/**
 * JSON: прочитать таблицу типов, напечатать пакет объявления.
 *
 * Своё, а не библиотечное, и причина простая: у этой библиотеки не должно быть
 * ни одной зависимости. Объявление приложения -- это то, с чего человек
 * начинает знакомство с фреймворком, и требовать ради него настроенного
 * Gradle с чужими артефактами значит поставить забор там, где нужна дверь.
 * Компилируется одним `kotlinc`, и этого достаточно.
 *
 * Значения ходят обычными типами Kotlin: `Map`, `List`, `String`, `Long`,
 * `Double`, `Boolean`, `null`. Своего дерева узлов нет намеренно -- пакет
 * объявления живёт полминуты между печатью и чтением, и заводить ради него
 * типы значило бы описать JSON дважды.
 */
object Json {

    fun write(value: Any?): String = StringBuilder().also { render(value, it) }.toString()

    private fun render(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is Double, is Float -> out.append(plainNumber(value))
            is String -> string(value, out)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    string(key.toString(), out)
                    out.append(':')
                    render(item, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    render(item, out)
                }
                out.append(']')
            }
            else -> throw OneFrameworkError(
                "в JSON не записывается: ${value::class.simpleName}. " +
                    "Объявление везёт только словари, списки, строки, числа и истину."
            )
        }
    }

    /**
     * Число без экспоненты: `1.0E7` -- законный JSON, но питон прочитает его
     * float-ом, а Kotlin печатал бы иначе, чем JavaScript. Расхождение в записи
     * числа -- это расхождение отпечатка, то есть два разных приложения там,
     * где объявлено одно.
     */
    private fun plainNumber(value: Any): String {
        val number = (value as Number).toDouble()
        if (number == kotlin.math.floor(number) && !number.isInfinite()) {
            return number.toLong().toString()
        }
        return number.toString()
    }

    private fun string(value: String, out: StringBuilder) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else ->
                    if (ch < ' ') {
                        out.append("\\u")
                        val hex = ch.code.toString(16)
                        repeat(4 - hex.length) { out.append('0') }
                        out.append(hex)
                    } else {
                        out.append(ch)
                    }
            }
        }
        out.append('"')
    }

    fun parse(text: String): Any? = Reader(text).let { reader ->
        val value = reader.value()
        reader.skipSpace()
        if (!reader.done()) throw OneFrameworkError("после JSON остался мусор на позиции ${reader.position}")
        value
    }

    private class Reader(private val text: String) {
        var position = 0

        fun done() = position >= text.length

        fun skipSpace() {
            while (position < text.length && text[position].isWhitespace()) position += 1
        }

        fun value(): Any? {
            skipSpace()
            if (done()) throw OneFrameworkError("JSON оборвался")
            return when (text[position]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            position += 1
            skipSpace()
            if (text[position] == '}') { position += 1; return out }
            while (true) {
                skipSpace()
                val key = string()
                skipSpace()
                expect(':')
                out[key] = value()
                skipSpace()
                when (text[position]) {
                    ',' -> position += 1
                    '}' -> { position += 1; return out }
                    else -> throw OneFrameworkError("в объекте ждали , или } на позиции $position")
                }
            }
        }

        private fun array(): List<Any?> {
            val out = ArrayList<Any?>()
            position += 1
            skipSpace()
            if (text[position] == ']') { position += 1; return out }
            while (true) {
                out.add(value())
                skipSpace()
                when (text[position]) {
                    ',' -> position += 1
                    ']' -> { position += 1; return out }
                    else -> throw OneFrameworkError("в списке ждали , или ] на позиции $position")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (text[position] != '"') {
                val ch = text[position]
                if (ch == '\\') {
                    position += 1
                    when (val escaped = text[position]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('')
                        'u' -> {
                            out.append(text.substring(position + 1, position + 5).toInt(16).toChar())
                            position += 4
                        }
                        else -> throw OneFrameworkError("неизвестный escape \\$escaped")
                    }
                } else {
                    out.append(ch)
                }
                position += 1
            }
            position += 1
            return out.toString()
        }

        private fun number(): Any {
            val start = position
            while (position < text.length && (text[position].isDigit() ||
                    text[position] in "-+.eE")
            ) position += 1
            val raw = text.substring(start, position)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()
                ?: throw OneFrameworkError("это не число: $raw")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, position)) {
                throw OneFrameworkError("ждали $word на позиции $position")
            }
            position += word.length
            return value
        }

        private fun expect(ch: Char) {
            if (text[position] != ch) throw OneFrameworkError("ждали $ch на позиции $position")
            position += 1
        }
    }
}

/** Ошибка объявления -- то же, чем в питоне является `DslError`. */
class OneFrameworkError(message: String) : RuntimeException(message)
