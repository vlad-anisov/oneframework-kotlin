package oneframework

/** Действия -- то, что стоит за кнопкой. */
abstract class Action(val defaultIcon: String?, val defaultStyle: String) {
    abstract fun document(): Map<String, Any?>
}

class SaveAction : Action("check", "default") {
    override fun document() = mapOf("type" to "save")
}

fun Save(): Action = SaveAction()

class DeleteAction(
    private val confirm: Any,
    private val swipe: Boolean,
    private val model: Model?,
    private val domain: Any?,
) : Action("delete", "destructive") {
    override fun document() = mapOf(
        "type" to "delete",
        // Строка -- вопрос, который задают; `true` -- «спросить словами
        // каркаса»; `false` -- не спрашивать.
        "confirm" to if (confirm is Boolean) confirm else textJson(confirm),
        "swipe" to swipe,
        "model" to model?.name,
        "record_id" to null,
        "domain" to exprJson(domain),
    )
}

fun Delete(
    confirm: Any = true,
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

/** Позвать логику модели. */
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
