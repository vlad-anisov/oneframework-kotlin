package oneframework

import kotlin.reflect.full.declaredMemberFunctions

/**
 * Печать объявления -- единственное, что у этой библиотеки осталось от JVM.
 *
 * Здесь и только здесь нужно **отражение**: чтобы узнать, какие у модели
 * методы, не заставляя разработчика их перечислять. На устройстве отражения
 * нет и не надо -- туда едет скомпилированный модуль с готовой точкой входа,
 * а имена в нём уже проставлены сборкой.
 *
 * Поэтому файл лежит отдельно от общей части: под WebAssembly он не
 * компилируется и не мешает.
 */

/** Не действия: питоновское соглашение про `_`, и то, что модель умеет сама. */
private val NOT_ACTIONS = setOf(
    "create", "search", "delete", "save", "field", "document", "typeDocument",
    "declareAction", "action", "equals", "hashCode", "toString",
)

/**
 * Найти методы моделей и объявить их действиями.
 *
 * Правило то же, что в питоне: действие -- это метод, а не помеченный метод.
 * Не действия только имена с `_` (в Kotlin это соглашение читается так же) и
 * то, что модель умеет сама.
 */
fun discoverActions(app: App) {
    for (model in app.models) {
        val found = model::class.declaredMemberFunctions
            .map { it.name }
            .filter { !it.startsWith("_") && it !in NOT_ACTIONS }
            .sorted()
        for (entry in found) model.declareAction(entry)
    }
}

/** Напечатать пакет объявления. Этим и заканчивается сборка на Kotlin. */
fun emit(app: App) {
    discoverActions(app)
    println(Json.write(declare(app)))
}
