package oneframework

/**
 * Таблица типов полей -- та же, что у питона и у JavaScript.
 *
 * Едет исходником внутри библиотеки (`FieldTypes.kt`), а не ресурсом: ресурс
 * читает только JVM, а эта библиотека собирается ещё и под WebAssembly.
 * Совпадение копии с `protocol/field-types.json` сторожит
 * `tests/together/test_protocol.py`.
 */
object Types {

    private val table: Map<String, Any?> = load()

    @Suppress("UNCHECKED_CAST")
    val all: Map<String, Map<String, Any?>> = table["types"] as Map<String, Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    val systemFields: Map<String, Map<String, Any?>> =
        table["system_fields"] as Map<String, Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    val systemFieldOrder: List<String> = table["system_field_order"] as List<String>

    fun of(ftype: String): Map<String, Any?> = all[ftype]
        ?: throw OneFrameworkError(
            "неизвестный тип поля «$ftype». Известны: ${all.keys.sorted().joinToString(", ")}."
        )

    @Suppress("UNCHECKED_CAST")
    fun widgets(ftype: String): List<String> = of(ftype)["widgets"] as List<String>

    fun widget(ftype: String): String = of(ftype)["widget"] as String

    fun sql(ftype: String): String? = of(ftype)["sql"] as String?

    fun stored(ftype: String): Boolean = of(ftype)["stored"] as Boolean

    @Suppress("UNCHECKED_CAST")
    fun defaults(ftype: String): Map<String, Any?> =
        (of(ftype)["defaults"] as Map<String, Any?>?) ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun semantics(ftype: String): List<String> =
        (of(ftype)["semantics"] as List<String>?) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun load(): Map<String, Any?> = Json.parse(FIELD_TYPES_JSON) as Map<String, Any?>
}

/** «Не то ли вы имели в виду» -- та же подсказка, что даёт питон. */
fun didYouMean(name: String, options: Collection<String>): String {
    val near = options
        .map { distance(name.lowercase(), it.lowercase()) to it }
        .filter { (d, option) -> d <= maxOf(1, option.length / 3) }
        .sortedBy { it.first }
        .map { it.second }
    return if (near.isEmpty()) "" else " Может быть, «${near.first()}»?"
}

private fun distance(a: String, b: String): Int {
    val row = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var corner = row[0]
        row[0] = i
        for (j in 1..b.length) {
            val previous = row[j]
            row[j] = minOf(row[j] + 1, row[j - 1] + 1, corner + if (a[i - 1] == b[j - 1]) 0 else 1)
            corner = previous
        }
    }
    return row[b.length]
}
