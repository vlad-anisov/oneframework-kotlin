package oneframework

/** Вид: что нарисовать и из чего. */
abstract class View(
    val name: String,
    val model: Model? = null,
    val title: String? = null,
    val dismiss: String = "auto",
    // Крошки: null -- решает правило (широкое окно, кадров больше одного),
    // false -- здесь их нет никогда, true -- есть и на телефоне.
    val crumbs: Boolean? = null,
) {
    private val state = LinkedHashMap<String, Field>()

    init {
        if (dismiss !in listOf("auto", "back", "close")) {
            throw OneFrameworkError("view «$name»: dismiss «$dismiss» -- так нельзя.")
        }
    }

    internal fun declareState(field: Field) {
        state[field.name] = field
    }

    /** Дерево вида. */
    abstract fun ui(): List<Node>

    /** Узлы списком. */
    protected fun nodes(vararg items: Node): List<Node> = items.toList()

    /** Документ вида -- то же, что печатает `document()` питона. */
    fun document(): Map<String, Any?> {
        val children = ui()
        assignIds(children, name)
        return mapOf(
            "type" to "view",
            "name" to name,
            "model" to model?.name,
            "children" to children.map { it.document() },
            "title" to (title ?: name),
            "title_is_code" to false,
            "dismiss" to dismiss,
            "crumbs" to crumbs,
            "state" to state.values.map { it.document() },
        )
    }
}
