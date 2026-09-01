package oneframework

/**
 * Узлы вида: строка, поле, кнопка, список, и действия за ними.
 *
 * Документ, который здесь печатается, совпадает с питоновским до последнего
 * ключа и до опознавательного номера узла (`Card.f1`, `Card.b2`). Совпадение
 * не украшение: по документу считается отпечаток, а по отпечатку обмен решает,
 * одно это приложение или два разных.
 *
 * Умеет столько, сколько нужно, чтобы объявить приложение целиком на Kotlin, и
 * ни узлом больше. Чего нет -- отказывает вслух: похожее хуже пустого места,
 * потому что не вызывает вопросов.
 */

/** Буква в номере узла. Та же таблица, что `_PREFIX` в питоне. */
private val PREFIX = mapOf(
    "view" to "v", "row" to "r", "col" to "c", "group" to "g", "section" to "sec",
    "repeat" to "rep", "tabs" to "tb", "tab" to "tab", "field" to "f", "list" to "l",
    "accordion" to "acc", "button" to "b", "search" to "s", "filter" to "flt",
    "sort" to "srt", "menu" to "m", "pill" to "p", "text" to "txt", "icon" to "ic",
)

abstract class Node(val nodeType: String) {
    var id: String? = null
        internal set
    open val children: List<Node> get() = emptyList()

    abstract fun document(): Map<String, Any?>

    fun walk(): List<Node> = listOf(this) + children.flatMap { it.walk() }
}

class FieldNode(
    private val field: Field,
    private val widget: String?,
    private val label: String?,
    private val visible: Any,
    private val place: String?,
    private val placeholder: String?,
    private val options: Map<String, Any?>,
) : Node("field") {

    init {
        if (widget != null && widget !in field.widgets) {
            throw OneFrameworkError(
                "виджет «$widget» не подходит полю ${field.name} (${field.ftype})." +
                    didYouMean(widget, field.widgets) +
                    " Годятся: ${field.widgets.sorted().joinToString(", ")}."
            )
        }
        if (place !in listOf(null, "navbar", "navbar-left", "after")) {
            throw OneFrameworkError("поле place = \"$place\" -- так нельзя.")
        }
    }

    override fun document(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(options)
        // То, что нужно рендереру и что знает о себе само поле. Список тот же,
        // что в питоне: перечислить его здесь заново -- значит однажды отстать.
        for (key in listOf(
            "currency", "digits", "maximum", "accept", "max_size", "inverse",
            "unit", "semantic", "lines", "unique", "create",
        )) {
            if (field.props.containsKey(key) && key !in out) out[key] = field.props[key]
        }
        return mapOf(
            "type" to "field",
            "id" to id,
            "name" to field.name,
            "scope" to if (field.owner is View) "view" else "record",
            "ftype" to field.ftype,
            "widget" to (widget ?: field.defaultWidget),
            "label" to (label ?: field.displayLabel),
            "required" to field.required,
            "readonly" to field.readonly,
            "visible" to conditionJson(visible),
            "place" to place,
            "placeholder" to placeholder,
            "options" to out,
        )
    }
}

class RowNode(override val children: List<Node>) : Node("row") {
    override fun document() =
        mapOf("type" to "row", "id" to id, "children" to children.map { it.document() })
}

fun row(vararg children: Node): Node = RowNode(children.toList())

class ButtonNode(
    private val label: String?,
    private val icon: String?,
    private val action: Action,
    private val style: String?,
    private val place: String?,
    private val enabled: Any,
    private val visible: Any,
) : Node("button") {

    init {
        if (place !in listOf(null, "navbar", "navbar-left", "fab", "after")) {
            throw OneFrameworkError("Button(place = \"$place\") -- так нельзя.")
        }
    }

    override fun document() = mapOf(
        "type" to "button",
        "id" to id,
        "label" to label,
        "visible" to conditionJson(visible),
        // Кнопка со словами не нуждается в картинке, повторяющей те же слова.
        "icon" to (icon ?: if (label != null) null else action.defaultIcon),
        "style" to (style ?: action.defaultStyle),
        "place" to place,
        "enabled" to conditionJson(enabled),
        "action" to action.document(),
    )
}

fun button(
    label: String? = null,
    icon: String? = null,
    action: Action,
    style: String? = null,
    place: String? = null,
    enabled: Any = true,
    visible: Any = true,
): Node = ButtonNode(label, icon, action, style, place, enabled, visible)

/**
 * Кнопка, за которой стоит логика модели: `button(action = Note.summary)`.
 *
 * Обёртки `Logic(...)` не нужно -- при ссылке она ничего не добавляла, а
 * лишнее слово между кнопкой и тем, что она делает, читается как обряд.
 * Отдельная перегрузка, а не общий тип: Kotlin типизирован, и пусть
 * компилятор видит, что именно кнопке дали.
 */
fun button(
    label: String? = null,
    icon: String? = null,
    action: DeviceAction,
    style: String? = null,
    place: String? = null,
    enabled: Any = true,
    visible: Any = true,
): Node = ButtonNode(label, icon, Logic(action), style, place, enabled, visible)

class ListNode(
    private val model: Model,
    private val item: View?,
    private val open: View?,
    private val label: String?,
    private val empty: List<String>?,
    private val domain: Any?,
    private val order: List<Any?>,
    private val display: String,
    private val index: Boolean,
    private val pageSize: Int,
    private val rowHeight: Int,
) : Node("list") {

    override fun document() = mapOf(
        "type" to "list",
        "id" to id,
        "model" to model.name,
        "model_label" to model.label,
        "label" to label,
        "menu" to null,
        "item" to item?.name,
        "open" to open?.name,
        "swipe_label" to null,
        "row_height" to rowHeight,
        "empty" to empty,
        "display" to display,
        "index" to index,
        "columns" to emptyList<Any?>(),
        "own_columns" to false,
        "page_size" to pageSize,
        "handle_field" to null,
        "handle_hidden" to false,
        "swipe_delete" to null,
        "search" to null,
        "domain" to exprJson(domain),
        "order" to order.map { exprJson(it) },
        "column_nodes" to null,
    )
}

fun list(
    model: Model,
    item: View? = null,
    open: View? = null,
    label: String? = null,
    empty: List<String>? = null,
    domain: Any? = null,
    order: List<Any?> = emptyList(),
    display: String = "auto",
    index: Boolean = false,
    pageSize: Int = 60,
    rowHeight: Int = 52,
): Node = ListNode(model, item, open, label, empty, domain, order, display, index, pageSize, rowHeight)

/**
 * Раздать узлам номера. Обход тот же, что у питона: сам вид, затем дети
 * вглубь, счётчик на каждую букву свой.
 */
internal fun assignIds(nodes: List<Node>, viewName: String) {
    val counters = HashMap<String, Int>()
    fun visit(node: Node) {
        val prefix = PREFIX[node.nodeType] ?: "n"
        val next = (counters[prefix] ?: 0) + 1
        counters[prefix] = next
        node.id = "$viewName.$prefix$next"
        node.children.forEach { visit(it) }
    }
    // Сам вид -- первый узел обхода, и он тоже забирает номер, хотя в
    // документе его не печатают. Не забрать значило бы сдвинуть все остальные.
    counters["v"] = 1
    nodes.forEach { visit(it) }
}
