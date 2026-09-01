package oneframework

/**
 * Приложение и пакет объявления.
 *
 * `app(...)` собирает то же, что питоновский `App`, а `emit(...)` печатает
 * **пакет объявления** -- обычный JSON, который читает сборка. Пакет и есть
 * граница: за ней сборке безразлично, чем приложение объявлено, и ровно
 * поэтому его можно объявить на Kotlin.
 */

/** Версия договора пакета. Та же, что в `oneframework/declaration.py`. */
const val VERSION = 1

class Screen(val view: View, val label: String? = null, val icon: String? = null)

class App(
    val screens: List<Screen>,
    val models: List<Model>,
    val views: List<View>,
    /**
     * Зависимости с Maven Central -- «группа:артефакт:версия».
     *
     * Едут не на устройство, а в сборку: TeaVM кладёт в модуль только тот код,
     * до которого дотянулась логика. Объявлять их приходится здесь, потому что
     * компилятору они нужны дважды -- когда собирается байткод и когда из
     * байткода делается модуль.
     */
    val dependencies: List<String> = emptyList(),
    title: String? = null,
    dbName: String? = null,
    val color: String = "#6750A4",
    // Согласие брать цвет у системы там, где платформа его даёт: из веба
    // его не достать, только родным кодом (docs/probe-system-color.md).
    val dynamicColor: Boolean = false,
    val locale: String? = null,
    val theme: String = "auto",
    val sync: Any? = null,
) {
    val rootView: View = screens.firstOrNull()?.view
        ?: throw OneFrameworkError("app(...) без единого экрана: показывать нечего.")
    val title: String = title ?: rootView.title ?: rootView.name
    val dbName: String = dbName ?: "${slug(this.title)}.db"

    init {
        val modelNames = models.map { it.name }.toSet()
        for (view in views) {
            val bound = view.model
            if (bound != null && bound.name !in modelNames) {
                throw OneFrameworkError(
                    "вид «${view.name}» привязан к модели «${bound.name}», " +
                        "которой нет в списке models приложения."
                )
            }
        }
        val viewNames = views.map { it.name }.toSet()
        for (screen in screens) {
            if (screen.view.name !in viewNames) {
                throw OneFrameworkError(
                    "экран показывает вид «${screen.view.name}», которого нет в списке views."
                )
            }
        }
    }
}

fun app(
    screens: List<Screen>,
    models: List<Model>,
    views: List<View>,
    dependencies: List<String> = emptyList(),
    title: String? = null,
    dbName: String? = null,
    color: String = "#6750A4",
    dynamicColor: Boolean = false,
    locale: String? = null,
    theme: String = "auto",
    sync: Any? = null,
): App = App(screens, models, views, dependencies, title, dbName, color, dynamicColor, locale, theme, sync)

/**
 * Приложение -> пакет объявления.
 *
 * Раздел `types` собирается из встреченных полей, а не перечисляется: так
 * новый тип поля попадает в пакет сам, как и в питоне.
 */
fun declare(application: App): Map<String, Any?> {
    val types = LinkedHashMap<String, Any?>()
    for (model in application.models) model.typeDocument(types)

    val logic = ArrayList<Map<String, Any?>>()
    for (model in application.models) {
        if (model.actions.isNotEmpty()) {
            logic.add(mapOf("actions" to model.actions.map { it.declaration() }))
        }
    }

    return mapOf(
        "oneframework" to VERSION,
        "app" to mapOf(
            "title" to application.title,
            "db_name" to application.dbName,
            "root" to application.rootView.name,
            "screens" to application.screens.map {
                mapOf(
                    "key" to it.view.name,
                    "label" to (it.label ?: it.view.title ?: it.view.name),
                    "icon" to it.icon,
                    "view" to it.view.name,
                    // Открытая запись становится рядом со списком, а не вместо
                    // него -- так делают обе платформы на широком экране.
                    "master_detail" to true,
                )
            },
            "color" to application.color,
            "dynamic_color" to application.dynamicColor,
            "locale" to application.locale,
            "theme" to application.theme,
            "sync" to application.sync,
            "python_packages" to emptyList<String>(),
            "maven" to application.dependencies,
        ),
        "types" to types,
        "models" to application.models.map { it.document() },
        "views" to application.views.map { it.document() },
        "logic" to logic,
        // Демо-данных эта библиотека не знает: `seed.py` -- приём питоновской
        // привязки. Ключ всё равно печатается -- пустой список значит «их
        // нет», отсутствие ключа значило бы потерянный раздел, и по пакету их
        // не различить.
        "seeds" to emptyList<Map<String, Any?>>(),
    )
}

private fun slug(text: String): String {
    val s = Regex("[^a-z0-9]+").replace(text.lowercase(), "_").trim('_')
    return if (s.isEmpty()) "app" else s
}
