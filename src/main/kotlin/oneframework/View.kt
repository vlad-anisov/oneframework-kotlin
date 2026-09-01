package oneframework

/**
 * Вид: что нарисовать и из чего.
 *
 *     object Line : View("Line", model = Note) {
 *         override fun ui() = nodes(
 *             row(
 *                 Note.done(widget = "toggle"),
 *                 Note.title(widget = "title"),
 *                 button(icon = "delete", action = Delete()),
 *             )
 *         )
 *     }
 *
 * Поле пишется `Note.title()`, а не `record.title()`, и это не отступление, а
 * то, что даёт статическая типизация: `Note.title` -- настоящее свойство,
 * которое компилятор проверяет, по которому работает переход к определению и
 * переименование. Прокси, разбирающий имена в рантайме, был бы здесь шагом
 * назад -- в питоне и JavaScript он нужен потому, что иначе имя поля никак не
 * проверить до запуска.
 *
 * Документ при этом получается тот же самый, до отпечатка.
 */
abstract class View(
    val name: String,
    val model: Model? = null,
    val title: String? = null,
    val dismiss: String = "auto",
    // Крошки: null -- решает правило (широкое окно, кадров больше одного),
    // false -- здесь их нет никогда, true -- есть и на телефоне. Тип с
    // вопросом, а не строка «auto»: у признака три состояния, и два из них --
    // обычные «да» и «нет».
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

    /** Дерево вида. Возвращает узлы -- один или несколько. */
    abstract fun ui(): List<Node>

    /** Узлы списком. Сахар, чтобы `ui()` читался как перечисление. */
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
