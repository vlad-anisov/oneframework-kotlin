package oneframework

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Поля модели.
 *
 * Объявляются делегатом -- `val title by string("Текст")`. Это не украшение:
 * имя поля обязано совпасть с именем свойства, иначе объявление говорит одно,
 * а колонка называется другим. Делегат берёт имя у самого свойства, и разойтись
 * им негде.
 *
 * Всё, что поле знает о своём типе, берётся из общей таблицы (`Types`), а не
 * пишется здесь: новый тип поля, добавленный в питон и попавший в таблицу,
 * появляется тут сам.
 */
open class Field(
    val ftype: String,
    val label: String? = null,
    val required: Boolean = false,
    val help: String? = null,
    widget: String? = null,
    props: Map<String, Any?> = emptyMap(),
) : Expr() {

    var name: String = ""
        internal set
    var owner: Any? = null
        internal set
    var readonly: Boolean = false
        internal set
    var system: Boolean = false
        internal set

    val props: MutableMap<String, Any?> = LinkedHashMap(Types.defaults(ftype)).apply { putAll(props) }
    val widgets: List<String> = Types.widgets(ftype)
    var defaultWidget: String = widget ?: Types.widget(ftype)
        internal set

    init {
        if (widget != null && widget !in widgets) {
            throw OneFrameworkError(
                "виджет «$widget» не подходит полю типа «$ftype»." +
                    didYouMean(widget, widgets) +
                    " Годятся: ${widgets.sorted().joinToString(", ")}."
            )
        }
    }

    /** Подпись на экране: объявленная, иначе выведенная из имени. */
    val displayLabel: String
        get() = label ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    val stored: Boolean
        get() = props["stored"] as Boolean? ?: Types.stored(ftype)

    /** Поле -> документ. Слово в слово то, что печатает `field_schema` питона. */
    open fun document(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["name"] = name
        out["ftype"] = ftype
        if (!label.isNullOrEmpty()) out["label"] = label
        if (required) out["required"] = true
        if (!help.isNullOrEmpty()) out["help"] = help
        if (defaultWidget != Types.widget(ftype)) out["widget"] = defaultWidget
        for (key in props.keys.sorted()) {
            val value = props[key]
            if (value == null || value == false) continue
            if (key in out) continue
            out[key] = value
        }
        if (readonly) out["readonly"] = true
        if (system) out["system"] = true
        return out
    }

    /** `Note.title(widget = "title")` -- то же, что в питоне, и теми же скобками. */
    operator fun invoke(
        widget: String? = null,
        label: String? = null,
        visible: Any = true,
        place: String? = null,
        placeholder: String? = null,
        options: Map<String, Any?> = emptyMap(),
    ): Node = FieldNode(this, widget, label, visible, place, placeholder, options)
}

/**
 * Поле, ещё не знающее своего имени.
 *
 * Живёт ровно до `by`: делегат отдаёт имя свойства, поле его забирает и
 * встаёт в модель на своё место.
 */
class FieldBuilder(private val field: Field) {
    operator fun provideDelegate(owner: Model, property: KProperty<*>): ReadOnlyProperty<Model, Field> {
        field.name = property.name
        field.owner = owner
        owner.declare(field)
        return ReadOnlyProperty { _, _ -> field }
    }

    /** Состояние экрана объявляется тем же делегатом, но на виде. */
    operator fun provideDelegate(owner: View, property: KProperty<*>): ReadOnlyProperty<View, Field> {
        field.name = property.name
        field.owner = owner
        owner.declareState(field)
        return ReadOnlyProperty { _, _ -> field }
    }
}

private fun build(field: Field) = FieldBuilder(field)

fun string(
    label: String? = null,
    lines: Int = 1,
    semantic: String = "text",
    required: Boolean = false,
    help: String? = null,
    widget: String? = null,
): FieldBuilder {
    val semantics = Types.semantics("string")
    if (semantic !in semantics) {
        throw OneFrameworkError(
            "string(semantic = \"$semantic\") -- так нельзя." +
                didYouMean(semantic, semantics) +
                " Годятся: ${semantics.joinToString(", ")}."
        )
    }
    val field = Field(
        "string", label, required, help, widget,
        mapOf("lines" to lines, "semantic" to semantic),
    )
    if (widget == null) {
        if (semantic != "text") field.defaultWidget = semantic
        else if (lines > 1) field.defaultWidget = "textarea"
    }
    return build(field)
}

/** Многострочная строка -- `maxLines` на Android, `lineLimit` на iOS. */
fun text(
    label: String? = null,
    lines: Int = 4,
    required: Boolean = false,
    help: String? = null,
    widget: String? = null,
): FieldBuilder = string(label, lines, "text", required, help, widget)

fun boolean(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("boolean", label, required, help, widget))

fun integer(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("integer", label, required, help, widget))

fun float(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("float", label, required, help, widget))

fun color(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("color", label, required, help, widget))

fun date(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("date", label, required, help, widget))

fun datetime(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("datetime", label, required, help, widget))

fun time(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("time", label, required, help, widget))

fun json(label: String? = null, required: Boolean = false, help: String? = null, widget: String? = null) =
    build(Field("json", label, required, help, widget))

fun selection(
    choices: List<String>,
    label: String? = null,
    required: Boolean = false,
    widget: String? = null,
): FieldBuilder {
    if (choices.isEmpty()) {
        throw OneFrameworkError("selection(...) без вариантов не описывает ничего.")
    }
    return build(Field("selection", label, required, null, widget, mapOf("selection" to choices)))
}

fun many2one(
    comodel: Model,
    label: String? = null,
    required: Boolean = false,
    widget: String? = null,
): FieldBuilder = build(
    Field("many2one", label, required, null, widget, mapOf("comodel" to comodel.name))
)

fun one2many(
    comodel: Model,
    inverse: String,
    label: String? = null,
    widget: String? = null,
): FieldBuilder = build(
    Field("one2many", label, false, null, widget, mapOf("comodel" to comodel.name, "inverse" to inverse))
)

fun many2many(comodel: Model, label: String? = null, widget: String? = null): FieldBuilder =
    build(Field("many2many", label, false, null, widget, mapOf("comodel" to comodel.name)))
