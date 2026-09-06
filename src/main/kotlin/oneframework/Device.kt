package oneframework

/** Действие модели -- её же метод. */
class DeviceAction internal constructor(
    val entry: String,
    private val model: Model,
    private val declaredWrites: List<Field>?,
    private val label: String?,
) {
    val name: String = "${model.name}.$entry"

    /** Что этому действию позволено записать. */
    val writes: List<String>
        get() = declaredWrites?.map { it.name } ?: model.fields.map { it.name }

    fun declaration(): Map<String, Any?> = mapOf(
        "name" to name,
        "label" to (label ?: entry),
        "args" to listOf(mapOf("name" to "ids", "type" to "ids")),
        "returns" to listOf(mapOf("name" to "records", "type" to "json")),
        "language" to "kotlin",
        "model" to model.name,
        // Байты кладёт сборка.
        "wasm" to mapOf(
            "module" to model.name,
            "entry" to entry,
            "writes" to writes,
            "sources" to emptyList<String>(),
        ),
    )
}
