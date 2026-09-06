package oneframework

/** Выражения: ссылка на поле, сравнение, «и»/«или»/«не». */
abstract class Expr {
    fun asc(): OrderExpr = OrderExpr(this, "asc")
    fun desc(): OrderExpr = OrderExpr(this, "desc")
}

class OrderExpr(val ref: Any?, val direction: String)

/** Выражение строкой: `expr("record.n * 2 > 10")`. */
fun expr(текст: String): Expr = TextExpr(текст)

class TextExpr(val текст: String) : Expr()

/** Ссылка на состояние экрана: `view.tag`, объявленное на самом виде. */
class StateRef(val fieldName: String) : Expr()

/** Дерево -> JSON. */
fun exprJson(node: Any?): Any? = when (node) {
    null -> null
    is Boolean, is String, is Int, is Long, is Double -> node
    is Field -> if (node.owner is View) mapOf("v" to node.name) else mapOf("r" to node.name)
    is StateRef -> mapOf("v" to node.fieldName)
    is ItemRef -> mapOf("i" to node.fieldName)
    is Template -> mapOf("fmt" to node.parts.map { exprJson(it) })
    is TextExpr -> mapOf("text" to node.текст)
    is OrderExpr -> mapOf("order" to exprJson(node.ref), "dir" to node.direction)
    is List<*> -> node.map { exprJson(it) }
    else -> throw OneFrameworkError(
        "в документ не записывается: ${node::class.simpleName}. " +
            "Условие пишется строкой: expr(\"record.done & !record.archived\")."
    )
}

/** `visible =` и `enabled =`: ответ, если он уже есть, иначе само условие. */
fun conditionJson(value: Any?): Any? = if (value is Boolean) value else exprJson(value)

/** `item.<поле>` -- запись, которую сейчас рисует повторитель. */
class ItemRef(val fieldName: String) : Expr()

/** Строка со ссылками -- `«{item.name}»`. */
class Template(val parts: List<Any?>)

private val PLACEHOLDER = Regex("""\{([A-Za-z_]\w*)\.([A-Za-z_]\w*)}""")

/** Текст -> [Template], если в нём есть ссылки, иначе он сам. */
fun parseTemplate(text: Any?): Any? {
    if (text !is String || !text.contains("{")) return text
    val parts = ArrayList<Any?>()
    var last = 0
    for (m in PLACEHOLDER.findAll(text)) {
        val (scope, name) = m.destructured
        if (scope != "item") {
            throw OneFrameworkError(
                "В шаблоне «$text» ссылка «$scope.$name»: подставляется только " +
                    "«item.<поле>» -- запись повторителя. Условие о записи или о " +
                    "состоянии экрана говорится через visible."
            )
        }
        if (m.range.first > last) parts.add(text.substring(last, m.range.first))
        parts.add(ItemRef(name))
        last = m.range.last + 1
    }
    if (parts.isEmpty()) return text
    if (last < text.length) parts.add(text.substring(last))
    return Template(parts)
}

/** Текст узла так, как он едет в документе: строка собой, шаблон -- деревом. */
fun textJson(value: Any?): Any? {
    val parsed = if (value is String) parseTemplate(value) else value
    return if (parsed == null || parsed is String) parsed else exprJson(parsed)
}
