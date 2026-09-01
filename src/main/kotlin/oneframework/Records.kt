package oneframework

/**
 * Набор записей -- то, чем зовут метод модели. Это и есть его `self`.
 *
 * Живёт в общей части библиотеки, потому что нужен там, где логика и
 * работает, -- в модуле WebAssembly на устройстве. Ни JVM, ни файлов, ни
 * ресурсов: только Kotlin, какой понимают оба компилятора.
 *
 * Поле спрашивается **самим полем**, а не строкой: `record[title]`. Kotlin
 * типизирован, и это его преимущество надо тратить, а не обходить -- опечатку
 * в имени поля здесь ловит компилятор, а не устройство.
 */
class Record internal constructor(
    private val values: MutableMap<String, Any?>,
    private val writes: Set<String>,
) {
    internal val changed = LinkedHashMap<String, Any?>()

    val id: String get() = values["id"] as? String ?: ""

    operator fun get(field: Field): Any? = values[field.name]

    /** Значение строкой -- самый частый случай, чтобы не приводить руками. */
    fun text(field: Field): String = values[field.name] as? String ?: ""

    fun flag(field: Field): Boolean = values[field.name] == true

    operator fun set(field: Field, value: Any?) {
        if (field.name !in writes) {
            throw OneFrameworkError(
                "поле «${field.name}» этому действию писать не разрешено; " +
                    "разрешены: ${writes.joinToString(", ").ifEmpty { "ни одного" }}. " +
                    "Считает действие, а пишет хост -- список закрытый намеренно."
            )
        }
        values[field.name] = value
        changed[field.name] = value
    }
}

class Records internal constructor(private val rows: List<Record>) : Iterable<Record> {
    override fun iterator(): Iterator<Record> = rows.iterator()

    val size: Int get() = rows.size

    fun isEmpty(): Boolean = rows.isEmpty()

    companion object {
        /**
         * Разобрать кадр, приехавший через границу стековой машины.
         *
         * У WebAssembly нет ни словарей, ни списков -- через границу ходит
         * UTF-8 JSON (`protocol/logic.json`). Разбор здесь, а не в теле
         * метода: метод пишут про предметную область, а не про протокол.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromJson(frame: String, writes: List<String>): Records {
            val parsed = Json.parse(frame) as? Map<String, Any?> ?: emptyMap()
            val rows = (parsed["records"] as? List<Any?> ?: emptyList()).map { row ->
                Record(LinkedHashMap(row as Map<String, Any?>), writes.toSet())
            }
            return Records(rows)
        }
    }

    /** Что изменилось -- в том виде, какого ждёт хост. Пишет он, а не мы. */
    fun changedJson(): String {
        val written = rows.filter { it.changed.isNotEmpty() }
            .map { LinkedHashMap(it.changed).apply { put("id", it.id) } }
        return Json.write(mapOf("records" to written))
    }
}
