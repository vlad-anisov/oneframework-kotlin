package oneframework

/**
 * Действие модели -- её же метод.
 *
 *     object Note : Model("Note") {
 *         val title by string("Текст")
 *         val details by text("Подробности")
 *
 *         fun summary(self: Records) {
 *             for (record in self) record[details] = record.text(title).uppercase()
 *         }
 *     }
 *
 * Помечать метод нечем и незачем -- ровно как в питоне и в JavaScript. Имена
 * методов находит отражение, когда объявление печатают; на устройстве
 * отражение не нужно, там уже скомпилированный модуль с готовой точкой входа.
 *
 * Отдельного файла с логикой больше нет. Он был нужен, пока библиотека
 * собиралась только под JVM: объявление шло под одну цель, логика под другую,
 * и один файл под две цели не собирался. Теперь библиотека общая -- ни `java`,
 * ни ресурсов, ни отражения в общей части, -- и оба компилятора берут один и
 * тот же файл приложения.
 */
class DeviceAction internal constructor(
    val entry: String,
    private val model: Model,
    private val declaredWrites: List<Field>?,
    private val label: String?,
) {
    val name: String = "${model.name}.$entry"

    /**
     * Что этому действию позволено записать. По умолчанию -- поля своей
     * модели: перечислять их руками значило бы пересказывать тело.
     */
    val writes: List<String>
        get() = declaredWrites?.map { it.name } ?: model.fields.map { it.name }

    fun declaration(): Map<String, Any?> = mapOf(
        "name" to name,
        "label" to (label ?: entry),
        "args" to listOf(mapOf("name" to "ids", "type" to "ids")),
        "returns" to listOf(mapOf("name" to "records", "type" to "json")),
        "language" to "kotlin",
        "model" to model.name,
        // Байты кладёт сборка. Чем именно её кормить -- дело сборки: она
        // знает, где лежат исходники библиотеки и файл приложения.
        "wasm" to mapOf(
            "module" to model.name,
            "entry" to entry,
            "writes" to writes,
            "sources" to emptyList<String>(),
        ),
    )
}
