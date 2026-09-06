package oneframework

import kotlin.reflect.full.declaredMemberFunctions

/** Печать объявления -- единственное, что у этой библиотеки осталось от JVM. */

/** Не действия: питоновское соглашение про `_`, и то, что модель умеет сама. */
private val NOT_ACTIONS = setOf(
    "create", "search", "delete", "save", "field", "document", "typeDocument",
    "declareAction", "action", "equals", "hashCode", "toString",
)

/** Найти методы моделей и объявить их действиями. */
fun discoverActions(app: App) {
    for (model in app.models) {
        val found = model::class.declaredMemberFunctions
            .map { it.name }
            .filter { !it.startsWith("_") && it !in NOT_ACTIONS }
            .sorted()
        for (entry in found) model.declareAction(entry)
    }
}

/** Напечатать пакет объявления. */
fun emit(app: App) {
    discoverActions(app)
    println(Json.write(declare(app)))
}
