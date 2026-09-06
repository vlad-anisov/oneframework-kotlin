package oneframework

/** Модель: имя, подпись, поля, логика. */
abstract class Model(
    val name: String,
    label: String? = null,
    table: String? = null,
) {
    val label: String = label ?: name
    val table: String = table ?: tableName(name)

    private val declared = LinkedHashMap<String, Field>()
    private val logic = ArrayList<DeviceAction>()

    val fields: List<Field> get() = declared.values.toList()
    val actions: List<DeviceAction> get() = logic.toList()

    internal fun declare(field: Field) {
        if (field.name in Types.systemFieldOrder) {
            throw OneFrameworkError(
                "$name.${field.name}: это поле модель получает даром, объявлять его не нужно."
            )
        }
        declared[field.name] = field
    }

    /** Объявить действие по имени метода. */
    fun declareAction(entry: String, writes: List<Field>? = null, label: String? = null) {
        logic.add(DeviceAction(entry, this, writes, label))
    }

    /** `Note.create(open = Card)` -- завести запись. */
    fun create(
        open: View? = null,
        values: Map<String, Any?> = emptyMap(),
        draft: Boolean = false,
        target: String = "page",
    ): Action = CreateAction(this, open, values, draft, target)

    /** `Note.search(domain)` -- набор записей по условию. */
    fun search(domain: Any? = null): RecordSet = RecordSet(this, domain)

    /** Действие модели по имени -- чтобы кнопка могла на него сослаться. */
    fun action(entry: String): DeviceAction = logic.firstOrNull { it.entry == entry }
        ?: throw OneFrameworkError(
            "у модели $name нет действия «$entry»." +
                didYouMean(entry, logic.map { it.entry })
        )

    /** `Note.delete()` -- убрать запись, которую сейчас рисуют. */
    fun delete(confirm: Boolean = true, swipe: Boolean = false): Action =
        DeleteAction(confirm, swipe, null, null)

    /** `Note.save()` -- записать черновик. */
    fun save(): Action = SaveAction()

    fun field(fieldName: String): Field = declared[fieldName]
        ?: throw OneFrameworkError(
            "у модели $name нет поля «$fieldName»." + didYouMean(fieldName, declared.keys)
        )

    /** Документ модели -- ровно то, что печатает `model_schema` питона. */
    fun document(): Map<String, Any?> {
        val out = ArrayList<Map<String, Any?>>()
        for (field in declared.values) out.add(field.document())
        for (systemName in Types.systemFieldOrder) out.add(Types.systemFields.getValue(systemName))
        return mapOf("name" to name, "label" to label, "table" to table, "fields" to out)
    }

    /** Типы, встреченные в этой модели: раздел `types` пакета собирается из них. */
    fun typeDocument(into: MutableMap<String, Any?>) {
        val ftypes = declared.values.map { it.ftype } +
            Types.systemFieldOrder.map { Types.systemFields.getValue(it)["ftype"] as String }
        for (ftype in ftypes) {
            if (ftype in into) continue
            into[ftype] = mapOf(
                "sql" to Types.sql(ftype),
                "widget" to Types.widget(ftype),
                "widgets" to Types.widgets(ftype),
                "stored" to Types.stored(ftype),
            )
        }
    }
}

/** Набор записей, названный условием. */
class RecordSet(private val model: Model, private val domain: Any?) {
    fun delete(confirm: Boolean = true, swipe: Boolean = false): Action =
        DeleteAction(confirm, swipe, model, domain)
}

/** `TodoLine` -> `todo_line`. */
fun tableName(name: String): String =
    Regex("(?<!^)([A-Z])").replace(name) { "_${it.groupValues[1]}" }.lowercase()
