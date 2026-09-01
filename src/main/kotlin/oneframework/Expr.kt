package oneframework

/**
 * Выражения: ссылка на поле, сравнение, «и»/«или»/«не».
 *
 * Форма записи -- не своя и здесь не пересказана: узлы и их ключи перечислены
 * один раз, в `protocol/expression.json`. Одно и то же условие обязано доехать
 * одинаковым деревом, с какого бы языка его ни объявили.
 *
 * Здесь записана та часть языка, которую библиотека умеет **своими** типами:
 * ссылки, сравнения, связки. Чего нет -- отказ вслух: молча доехавшее не то
 * условие показывает не те записи, и заметить это можно только по чужой
 * жалобе.
 *
 * Всё остальное -- арифметику, свёртки по набору, шаблоны строк -- пишут
 * строкой через [expr]. Разбирает её ядро, одним разборщиком на все языки:
 * встраивать четырнадцать родов узлов в каждый язык дорого, и по этой цене
 * Kotlin до сих пор умел семь из четырнадцати.
 */
abstract class Expr {
    infix fun and(other: Expr): Expr = BoolExpr("&", listOf(this, other))
    infix fun or(other: Expr): Expr = BoolExpr("|", listOf(this, other))
    operator fun not(): Expr = NotExpr(this)

    infix fun eq(value: Any?): Expr = CmpExpr("=", this, value)
    infix fun ne(value: Any?): Expr = CmpExpr("!=", this, value)
    infix fun gt(value: Any?): Expr = CmpExpr(">", this, value)
    infix fun ge(value: Any?): Expr = CmpExpr(">=", this, value)
    infix fun lt(value: Any?): Expr = CmpExpr("<", this, value)
    infix fun le(value: Any?): Expr = CmpExpr("<=", this, value)

    fun asc(): OrderExpr = OrderExpr(this, "asc")
    fun desc(): OrderExpr = OrderExpr(this, "desc")
}

class CmpExpr(val op: String, val left: Any?, val right: Any?) : Expr()
class BoolExpr(val op: String, val parts: List<Any?>) : Expr()
class NotExpr(val inner: Any?) : Expr()
class OrderExpr(val ref: Any?, val direction: String)

/**
 * Выражение строкой: `expr("record.n * 2 > 10")`.
 *
 * Дерево собирает ядро на сборке -- на устройство едет то же, что и от
 * питоновского приложения. Ошибку в строке ядро называет вслух, с местом; до
 * устройства неразобранное не доезжает.
 */
fun expr(текст: String): Expr = TextExpr(текст)

class TextExpr(val текст: String) : Expr()

/** Ссылка на состояние экрана: `view.tag`, объявленное на самом виде. */
class StateRef(val fieldName: String) : Expr()

/** Дерево -> JSON. Та же запись, что читает питон и рантайм на устройстве. */
fun exprJson(node: Any?): Any? = when (node) {
    null -> null
    is Boolean, is String, is Int, is Long, is Double -> node
    is Field -> if (node.owner is View) mapOf("v" to node.name) else mapOf("r" to node.name)
    is StateRef -> mapOf("v" to node.fieldName)
    is TextExpr -> mapOf("text" to node.текст)
    is CmpExpr -> mapOf("op" to node.op, "l" to exprJson(node.left), "r" to exprJson(node.right))
    is BoolExpr -> mapOf("op" to node.op, "p" to node.parts.map { exprJson(it) })
    is NotExpr -> mapOf("op" to "!", "e" to exprJson(node.inner))
    is OrderExpr -> mapOf("order" to exprJson(node.ref), "dir" to node.direction)
    is List<*> -> node.map { exprJson(it) }
    else -> throw OneFrameworkError(
        "в документ не записывается: ${node::class.simpleName}. Библиотека для " +
            "Kotlin умеет ссылки на поля, сравнения и связки «и»/«или»/«не»."
    )
}

/** `visible =` и `enabled =`: ответ, если он уже есть, иначе само условие. */
fun conditionJson(value: Any?): Any? = if (value is Boolean) value else exprJson(value)
