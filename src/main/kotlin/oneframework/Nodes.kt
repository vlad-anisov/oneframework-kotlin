package oneframework

/** Узлы вида: строка, поле, кнопка, список, и действия за ними. */

/** Буква в номере узла. */
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

    /** Все узлы поддерева. */
    open fun walk(): List<Node> = listOf(this) + children.flatMap { it.walk() }
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
        // То, что нужно рендереру и что знает о себе само поле.
        for (key in listOf(
            "currency", "digits", "maximum", "accept", "max_size", "inverse",
            "unit", "semantic", "lines", "unique", "create",
        )) {
            if (field.props.containsKey(key) && key !in out) out[key] = field.props[key]
        }
        val доc = LinkedHashMap<String, Any?>()
        доc["type"] = "field"
        доc["id"] = id
        доc["name"] = field.name
        доc["scope"] = if (field.owner is View) "view" else "record"
        доc["ftype"] = field.ftype
        доc["widget"] = widget ?: field.defaultWidget
        доc["label"] = label ?: field.displayLabel
        доc["required"] = field.required
        доc["readonly"] = field.readonly
        доc["visible"] = conditionJson(visible)
        доc["place"] = place
        доc["placeholder"] = placeholder
        доc["options"] = out
        // Варианты выбора едут словарями: рендерер читает их по именам ключей,
        // а не по месту.
        if (field.ftype == "selection") {
            @Suppress("UNCHECKED_CAST")
            val pairs = field.props["selection"] as? List<List<String>> ?: emptyList()
            доc["choices"] = pairs.map { mapOf("value" to it[0], "label" to it[1]) }
        }
        // Рядом со связью -- то, чем её рисуют: подпись модели, поле показа и
        // поле цвета.
        field.comodel?.let { со ->
            val показ = со.fields.firstOrNull { it.name == "name" }
                ?: со.fields.firstOrNull { it.ftype == "string" && !it.system }
            val цвет = со.fields.firstOrNull { it.ftype == "color" }
            доc["comodel"] = mapOf(
                "name" to со.name, "label" to со.label,
                "display_field" to показ?.name, "color_field" to цвет?.name,
            )
        }
        return доc
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

    /** Место кнопки: по нему вкладка отбирает свою плавающую. */
    internal fun placeOf(): String? = place

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

/** Кнопка, за которой стоит логика модели: `button(action = Note.summary)`. */
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
    private val label: Any?,
    private val empty: List<String>?,
    private val domain: Any?,
    private val order: List<Any?>,
    private val display: String,
    private val index: Boolean,
    private val pageSize: Int,
    private val rowHeight: Int,
    internal val menu: MenuNode?,
    internal val search: SearchNode?,
    private val handleField: String?,
    private val handleHidden: Boolean,
    private val swipeLabel: String?,
    private val swipeDelete: String?,
) : Node("list") {

    override fun document() = mapOf(
        "type" to "list",
        "id" to id,
        "model" to model.name,
        "model_label" to model.label,
        "label" to textJson(label),
        "menu" to menu?.document(),
        "item" to item?.name,
        "open" to open?.name,
        "swipe_label" to swipeLabel,
        "row_height" to rowHeight,
        "empty" to empty,
        "display" to display,
        "index" to index,
        "columns" to emptyList<Any?>(),
        "own_columns" to false,
        "page_size" to pageSize,
        "handle_field" to handleField,
        "handle_hidden" to handleHidden,
        "swipe_delete" to swipeDelete,
        "search" to search?.document(handleField),
        "domain" to exprJson(domain),
        "order" to order.map { exprJson(it) },
        "column_nodes" to null,
    )
}

fun list(
    model: Model,
    item: View? = null,
    open: View? = null,
    label: Any? = null,
    empty: List<String>? = null,
    domain: Any? = null,
    order: List<Any?> = emptyList(),
    display: String = "auto",
    index: Boolean = false,
    pageSize: Int = 60,
    rowHeight: Int = 52,
    menu: MenuNode? = null,
    search: SearchNode? = null,
    handleField: String? = null,
    handleHidden: Boolean = false,
    swipeLabel: String? = null,
    swipeDelete: String? = null,
): Node = ListNode(
    model, item, open, label, empty, domain, order, display, index, pageSize,
    rowHeight, menu, search, handleField, handleHidden, swipeLabel, swipeDelete,
)

/** Раздать узлам номера. */
internal fun assignIds(nodes: List<Node>, viewName: String) {
    val counters = HashMap<String, Int>()
    fun visit(node: Node) {
        val prefix = PREFIX[node.nodeType] ?: "n"
        val next = (counters[prefix] ?: 0) + 1
        counters[prefix] = next
        node.id = "$viewName.$prefix$next"
        // Поиск и меню висят на списке, а не стоят среди детей, и обход обязан
        // их достать: номера раздаются одним проходом, а отбор без номера
        // рендерер не сумеет назвать в кадре.
        if (node is ListNode) {
            node.search?.let { s ->
                visit(s)
                (s.filters + s.sorts).forEach { visit(it) }
            }
            node.menu?.walk()?.forEach { visit(it) }
        }
    }
    fun обойти(node: Node) {
        visit(node)
        node.walk().drop(1).forEach { visit(it) }
    }
    // Сам вид -- первый узел обхода, и он тоже забирает номер, хотя в
    // документе его не печатают.
    counters["v"] = 1
    nodes.forEach { обойти(it) }
}

// --- Раскладка: колонка, группа, заголовок, складной блок -------------------

/** Вертикальная стопка. */
class ColNode(override val children: List<Node>, private val span: Int?) : Node("col") {
    override fun document() = mapOf(
        "type" to "col", "id" to id, "span" to span,
        "children" to children.map { it.document() },
    )
}

fun col(vararg children: Node, span: Int? = null): Node = ColNode(children.toList(), span)

/** Озаглавленная связка полей: раздел формы. */
class GroupNode(
    override val children: List<Node>,
    private val label: String?,
    private val cols: Int,
    private val surface: String,
) : Node("group") {

    init {
        if (surface !in listOf("card", "sheet")) {
            throw OneFrameworkError(
                "group(surface = \"$surface\") -- так нельзя; годятся card, sheet."
            )
        }
    }

    override fun document() = mapOf(
        "type" to "group", "id" to id, "label" to label, "cols" to cols,
        "surface" to surface, "children" to children.map { it.document() },
    )
}

/** Подложка группы. */
fun group(
    vararg children: Node,
    label: String? = null,
    cols: Int = 1,
    surface: String = "card",
): Node = GroupNode(children.toList(), label, cols, surface)

/** Заголовок между связками полей. */
class SectionNode(private val title: String?, private val subtitle: String?) : Node("section") {
    override fun document() =
        mapOf("type" to "section", "id" to id, "title" to title, "subtitle" to subtitle)
}

fun section(title: String?, subtitle: String? = null): Node = SectionNode(title, subtitle)

/** Озаглавленный блок, который складывается. */
class AccordionNode(
    override val children: List<Node>,
    private val label: String?,
    private val open: Boolean,
    private val visible: Any,
) : Node("accordion") {

    override fun document(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["type"] = "accordion"
        out["id"] = id
        out["label"] = label
        out["open"] = open
        out["children"] = children.map { it.document() }
        if (visible != true) out["visible"] = conditionJson(visible)
        return out
    }
}

fun accordion(
    vararg children: Node,
    label: String? = null,
    open: Boolean = false,
    visible: Any = true,
): Node = AccordionNode(children.toList(), label, open, visible)

// --- Подписи: текст, значок, счётчик ----------------------------------------

/** Кусок текста там, где стоял бы кусок текста. */
class TextNode(internal val value: Any?) : Node("text") {
    override fun document() = mapOf("type" to "text", "id" to id, "value" to textJson(value))
}

fun text(value: Any?): Node = TextNode(value)

/** Знак там, где стоял бы кусок текста -- `tab(icon("star"), ...)`. */
class IconNode(private val glyph: String) : Node("icon") {
    override fun document() = mapOf("type" to "icon", "id" to id, "name" to glyph)
}

fun icon(name: String): Node = IconNode(name)

/** Счётчик рядом с подписью. */
class PillNode(private val value: Any?, private val whenShown: String) : Node("pill") {

    init {
        if (whenShown !in listOf("always", "closed")) {
            throw OneFrameworkError(
                "pill(shown = \"$whenShown\") -- так нельзя; годятся always, closed."
            )
        }
    }

    override fun document(): Map<String, Any?> {
        // Считать нечего -- показывать нечего: нулевой значок это отметка со
        // словом «пусто», и обе платформы её не рисуют вовсе.
        val shown: Any? = when {
            value is Expr -> textJson(value)
            value == null || value == false || value == 0 -> null
            else -> value.toString()
        }
        return mapOf("type" to "pill", "id" to id, "value" to shown, "when" to whenShown)
    }
}

fun pill(value: Any?, shown: String = "always"): Node = PillNode(value, shown)

// --- Вкладки и повторитель --------------------------------------------------

/** Из чего складывается заголовок вкладки -- в отличие от её содержимого. */
private val TITLE_PARTS = setOf("text", "icon", "pill")

/** Одна страница вкладок. */
class TabNode(labelNode: Node, parts: List<Node>) : Node("tab") {
    private val title = ArrayList<Node>()
    private var fab: Node? = null
    private val content = ArrayList<Node>()

    init {
        title.add(labelNode)
        for (node in parts) {
            when {
                // Плавающая кнопка висит над всем экраном, а не лежит в
                // странице, и какая страница открыта -- знает только рендерер.
                node is ButtonNode && node.placeOf() == "fab" -> fab = node
                node.nodeType in TITLE_PARTS -> title.add(node)
                else -> content.add(node)
            }
        }
    }

    /** Простое имя вкладки -- первый текст заголовка. */
    private fun plainLabel(): Any? =
        (title.firstOrNull { it is TextNode } as TextNode?)?.value ?: ""

    override val children: List<Node> get() = content

    /** Части заголовка -- тоже узлы, и номера им нужны, как всякому другому. */
    override fun walk(): List<Node> =
        listOf(this) + title.flatMap { it.walk() } +
            (fab?.walk() ?: emptyList()) + content.flatMap { it.walk() }

    override fun document() = mapOf(
        "type" to "tab",
        "id" to id,
        "label" to textJson(plainLabel()),
        "title" to title.map { it.document() },
        "fab" to fab?.document(),
        "children" to content.map { it.document() },
    )
}

fun tab(label: String, vararg parts: Node): Node = TabNode(TextNode(label), parts.toList())

fun tab(label: Node, vararg parts: Node): Node = TabNode(label, parts.toList())

/** Несколько страниц, из которых видна одна. */
class TabsNode(override val children: List<Node>, private val page: Any) : Node("tabs") {

    init {
        for (child in children) {
            if (child !is TabNode && child !is RepeatNode && child !is ButtonNode) {
                throw OneFrameworkError(
                    "tabs(...) принимает страницы tab(...), repeat(...) из них и " +
                        "button(...) в конец полосы; пришло ${child.nodeType}."
                )
            }
        }
        if (page !in listOf("auto", true, false)) {
            throw OneFrameworkError("tabs(page = $page) -- так нельзя; годятся \"auto\", true, false.")
        }
    }

    override fun document() = mapOf(
        "type" to "tabs", "id" to id, "page" to page,
        "children" to children.map { it.document() },
    )
}

/** Являются ли вкладки самим экраном. */
fun tabs(vararg children: Node, page: Any = "auto"): Node = TabsNode(children.toList(), page)

/** Одно тело, нарисованное по разу на каждую запись модели. */
class RepeatNode(
    private val model: Model,
    override val children: List<Node>,
    private val domain: Any?,
    private val order: List<Any?>?,
) : Node("repeat") {

    override fun document(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["type"] = "repeat"
        out["id"] = id
        out["model"] = model.name
        out["children"] = children.map { it.document() }
        if (domain != null) out["domain"] = exprJson(domain)
        if (order != null) out["order"] = exprJson(order)
        return out
    }
}

/** Запись повторителя, привязанная к его модели. */
class Item internal constructor(private val model: Model) {
    operator fun invoke(field: Field): Expr {
        if (field.owner !== model) {
            throw OneFrameworkError(
                "item(${field.name}): поле принадлежит не ${model.name}, " +
                    "а модели ${(field.owner as? Model)?.name ?: "без имени"}."
            )
        }
        return ItemRef(field.name)
    }

    /** Ключ записи. */
    val id: Expr get() = ItemRef("id")
}

fun repeat(
    model: Model,
    domain: Any? = null,
    order: List<Any?>? = null,
    body: (Item) -> List<Node>,
): Node = RepeatNode(model, body(Item(model)), domain, order)

// --- Меню, поиск, отбор, порядок --------------------------------------------

/** Меню за тремя точками. */
class MenuNode(
    override val children: List<Node>,
    private val place: String?,
    private val glyph: String,
) : Node("menu") {

    init {
        if (place !in listOf(null, "navbar", "navbar-left")) {
            throw OneFrameworkError("menu(place = \"$place\") -- так нельзя.")
        }
    }

    override fun document() = mapOf(
        "type" to "menu", "id" to id, "place" to place, "icon" to glyph,
        "children" to children.map { it.document() },
    )
}

fun menu(vararg children: Node, place: String? = null, icon: String = "more_vert"): MenuNode =
    MenuNode(children.toList(), place, icon)

/** Именованный отбор в поиске списка: подпись и домен. */
class FilterNode(
    private val label: String,
    private val domain: Any?,
    internal val isDefault: Boolean,
) : Node("filter") {
    override fun document() = mapOf(
        "type" to "filter", "id" to id, "label" to label, "default" to isDefault,
        "domain" to exprJson(domain),
    )
}

fun filter(label: String, domain: Any? = null, default: Boolean = false): FilterNode =
    FilterNode(label, domain, default)

/** Именованный порядок из одного или нескольких членов. */
class SortNode(
    private val label: String,
    orders: List<Any?>,
    internal val isDefault: Boolean,
    private val section: Boolean,
) : Node("sort") {

    // Голое поле -- порядок по возрастанию: так его и пишут, и разворачивать
    // это здесь дешевле, чем требовать `.asc()` у каждого члена.
    private val orders: List<Any?> =
        orders.map { if (it is OrderExpr) it else OrderExpr(it, "asc") }

    init {
        if (orders.isEmpty()) {
            throw OneFrameworkError("sort(\"$label\") без поля: сортировать нечем.")
        }
    }

    /** Перетаскивание руками имеет смысл, только когда порядок и **есть** ручка. */
    internal fun reorderable(handleField: String?): Boolean {
        if (handleField == null || orders.size != 1) return false
        val one = orders[0]
        val asc = one !is OrderExpr || one.direction == "asc"
        val ref = if (one is OrderExpr) one.ref else one
        return asc && (ref as? Field)?.name == handleField
    }

    internal fun document(handleField: String?) = mapOf(
        "type" to "sort", "id" to id, "label" to label, "default" to isDefault,
        "section" to section, "reorderable" to reorderable(handleField),
        // Переключатель без порядка -- кнопка, которая ничего не значит.
        "orders" to orders.map { exprJson(it) },
    )

    override fun document() = document(null)
}

fun sort(
    label: String,
    vararg orders: Any?,
    default: Boolean = false,
    section: Boolean = false,
): SortNode = SortNode(label, orders.toList(), default, section)

/** Поля свободного поиска вместе с доступными отборами и порядками. */
class SearchNode(
    internal val fields: List<Field>,
    internal val filters: List<FilterNode>,
    internal val sorts: List<SortNode>,
    private val glyph: String,
) : Node("search") {

    private fun defaultFilter(): Int? = filters.indexOfFirst { it.isDefault }.takeIf { it >= 0 }

    private fun defaultSort(): Int? {
        val chosen = sorts.indexOfFirst { it.isDefault }
        if (chosen >= 0) return chosen
        return if (sorts.isEmpty()) null else 0
    }

    internal fun document(handleField: String?) = mapOf(
        "type" to "search",
        "icon" to glyph,
        "fields" to fields.map { it.name },
        "placeholder" to fields.joinToString(", ") { it.displayLabel },
        "filters" to filters.map { it.document() },
        "sorts" to sorts.map { it.document(handleField) },
        "default_filter" to defaultFilter(),
        "default_sort" to defaultSort(),
    )

    override fun document() = document(null)
}

/** Поля, отборы и порядки вперемешку -- разбираются по тому, чем они являются. */
fun search(vararg parts: Any, icon: String = "swap_vert"): SearchNode {
    val fields = ArrayList<Field>()
    val filters = ArrayList<FilterNode>()
    val sorts = ArrayList<SortNode>()
    for (part in parts) {
        when (part) {
            is FilterNode -> filters.add(part)
            is SortNode -> sorts.add(part)
            is Field -> fields.add(part)
            else -> throw OneFrameworkError(
                "search(...) принимает поля, filter(...) и sort(...); " +
                    "пришло ${part::class.simpleName}."
            )
        }
    }
    return SearchNode(fields, filters, sorts, icon)
}
