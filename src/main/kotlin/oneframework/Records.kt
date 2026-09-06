package oneframework

/** Набор записей -- то, чем зовут метод модели. */
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
        /** Разобрать кадр, приехавший через границу стековой машины. */
        @Suppress("UNCHECKED_CAST")
        fun fromJson(frame: String, writes: List<String>): Records {
            val parsed = Json.parse(frame) as? Map<String, Any?> ?: emptyMap()
            val rows = (parsed["records"] as? List<Any?> ?: emptyList()).map { row ->
                Record(LinkedHashMap(row as Map<String, Any?>), writes.toSet())
            }
            return Records(rows)
        }
    }

    /** Что изменилось -- в том виде, какого ждёт хост. */
    fun changedJson(): String {
        val written = rows.filter { it.changed.isNotEmpty() }
            .map { LinkedHashMap(it.changed).apply { put("id", it.id) } }
        return Json.write(mapOf("records" to written))
    }
}
