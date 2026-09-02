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
    is ItemRef -> mapOf("i" to node.fieldName)
    is IsNullExpr -> mapOf("op" to "null", "e" to exprJson(node.inner))
    is Template -> mapOf("fmt" to node.parts.map { exprJson(it) })
    is Aggregate -> {
        val out = LinkedHashMap<String, Any?>()
        out["agg"] = node.kind
        out["model"] = node.model.name
        if (node.via != null) out["via"] = node.via
        if (node.domain != null) out["domain"] = exprJson(node.domain)
        out
    }
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

/** `item.<поле>` -- запись, которую сейчас рисует повторитель. */
class ItemRef(val fieldName: String) : Expr()

/**
 * «Пусто ли» -- не сравнение с пустотой: в SQL `= NULL` не истина и не ложь, а
 * неизвестность, и такое условие не ответило бы ни да, ни нет.
 */
class IsNullExpr(val inner: Any?) : Expr()

fun Expr.isNull(): Expr = IsNullExpr(this)

fun Field.isNull(): Expr = IsNullExpr(this)

/**
 * Вопрос к **другой** модели: сколько там записей, есть ли хоть одна.
 *
 * Поле внутри запроса принадлежит **его** модели, а не окружающей: вопрос
 * может стоять в виде, не привязанном ни к какой модели, и его поля -- всё
 * равно колонки названной.
 */
class Aggregate(
    val kind: String,
    val model: Model,
    val domain: Any? = null,
    val via: String? = null,
) : Expr()

/** Сколько записей подошло. Читается числом. */
fun count(model: Model, domain: Any? = null, via: String? = null): Expr =
    Aggregate("count", model, domain, via)

/** Есть ли хоть одна подошедшая. Читается условием. */
fun exists(model: Model, domain: Any? = null, via: String? = null): Expr =
    Aggregate("exists", model, domain, via)

/**
 * Строка со ссылками -- `«{item.name}»`.
 *
 * В документ она едет **деревом**, а не строкой: подставлять в неё нечего, пока
 * документ не развёрнут на данных. Складывает её разворот, и рендерер про
 * шаблоны не знает и знать не должен.
 */
class Template(val parts: List<Any?>)

private val PLACEHOLDER = Regex("""\{([A-Za-z_]\w*)\.([A-Za-z_]\w*)}""")

/**
 * Текст -> [Template], если в нём есть ссылки, иначе он сам.
 *
 * Подставляется сегодня только `item.`: это то, что нужно повторителю, и то
 * единственное, на что при развороте есть ответ. Прочее названо в отказе, а не
 * забыто молча -- иначе строка приедет на экран с фигурными скобками, и никто
 * не поймёт почему.
 */
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
