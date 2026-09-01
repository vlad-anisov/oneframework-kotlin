package oneframework

/**
 * Действия -- то, что стоит за кнопкой.
 *
 * Форма записи в документе закреплена в `protocol/document.json` и совпадает с
 * питоновской. Кнопка о действии не знает ничего, кроме того, что оно есть:
 * `Note.summary` не говорит, на чём написана логика и чем её исполнят.
 */
abstract class Action(val defaultIcon: String?, val defaultStyle: String) {
    abstract fun document(): Map<String, Any?>
}

class SaveAction : Action("check", "default") {
    override fun document() = mapOf("type" to "save")
}

fun Save(): Action = SaveAction()

class DeleteAction(
    private val confirm: Boolean,
    private val swipe: Boolean,
    private val model: Model?,
    private val domain: Any?,
) : Action("delete", "destructive") {
    override fun document() = mapOf(
        "type" to "delete",
        "confirm" to confirm,
        "swipe" to swipe,
        "model" to model?.name,
        "record_id" to null,
        "domain" to exprJson(domain),
    )
}

fun Delete(
    confirm: Boolean = true,
    swipe: Boolean = false,
    model: Model? = null,
    domain: Any? = null,
): Action = DeleteAction(confirm, swipe, model, domain)

class CreateAction(
    private val model: Model,
    private val open: View?,
    private val values: Map<String, Any?>,
    private val draft: Boolean,
    private val target: String,
) : Action("add", "default") {
    override fun document() = mapOf(
        "type" to "create",
        "model" to model.name,
        "view" to open?.name,
        "values" to values.mapValues { exprJson(it.value) },
        "draft" to draft,
        "target" to target,
    )
}

fun Create(
    model: Model,
    open: View? = null,
    values: Map<String, Any?> = emptyMap(),
    draft: Boolean = false,
    target: String = "page",
): Action = CreateAction(model, open, values, draft, target)

class OpenAction(
    private val view: View,
    private val target: String,
) : Action(null, "default") {
    override fun document() = mapOf(
        "type" to "open", "view" to view.name, "record_id" to null, "target" to target,
    )
}

fun Open(view: View, target: String = "page"): Action = OpenAction(view, target)

class LogicAction(
    private val actionName: String,
    private val args: Map<String, Any?>,
    private val closesScreen: Boolean,
) : Action("play_arrow", "default") {
    override fun document() = mapOf(
        "type" to "logic",
        "name" to actionName,
        "args" to args.mapValues { exprJson(it.value) },
        "closes_screen" to closesScreen,
    )
}

/**
 * Позвать логику модели.
 *
 * Обычно не нужна: `Note.summary` сам себе действие, и `button(action = ...)`
 * принимает его прямо. Обёртка остаётся для случая, когда действию передают
 * доводы или оно закрывает экран.
 */
fun Logic(
    action: DeviceAction,
    args: Map<String, Any?> = emptyMap(),
    closesScreen: Boolean = false,
): Action = LogicAction(action.name, args, closesScreen)

fun Logic(
    name: String,
    args: Map<String, Any?> = emptyMap(),
    closesScreen: Boolean = false,
): Action = LogicAction(name, args, closesScreen)
