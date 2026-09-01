/**
 * Заметки на Kotlin -- ни строчки на другом языке.
 *
 * Три приложения этой тройки одинаковы снаружи: те же модели, тот же экран, та
 * же кнопка и тот же ответ. Различается ровно одно -- на чём написано
 * приложение и чем исполняется его логика на устройстве:
 *
 * * `notes-python` -- в приложение кладётся **дополнительный интерпретатор**
 *   (Pyodide, 13 МБ), и он исполняет исходник;
 * * `notes-js`     -- исполняет **встроенный** движок webview, класть нечего;
 * * `notes-kotlin` -- на сборке получается **скомпилированный модуль** `.wasm`,
 *   и он инстанцируется как машинный код.
 *
 * Одинаковы они не на глаз: объявления всех трёх дают побайтово одни и те же
 * документы моделей и видов, и это закреплено `tests/test_three_languages.py`.
 *
 *     oneframework build web examples/notes-kotlin/App.kt
 *
 * Файл один и тот же собирается дважды: под JVM -- чтобы напечатать
 * объявление на машине разработчика, и под WebAssembly -- чтобы логика
 * работала на устройстве. Библиотека объявления для этого сделана общей:
 * ни `java`, ни ресурсов, ни отражения в ней нет.
 */

package notes

import org.apache.commons.text.WordUtils

import oneframework.Model
import oneframework.Records
import oneframework.Screen
import oneframework.View
import oneframework.app
import oneframework.boolean
import oneframework.button
import oneframework.list
import oneframework.row
import oneframework.string
import oneframework.text

object Note : Model("Note", label = "Заметка") {
    val title by string("Текст", required = true)
    val details by text("Подробности")
    val done by boolean("Выполнено")

    /**
     * Пересчитать сводку по тексту заметки.
     *
     * Метод модели -- уже действие: помечать нечем и незачем, ровно как в
     * питоне и в JavaScript. `self` -- набор записей, по которым его позвали;
     * правка присваиванием, возвращать нечего.
     *
     * Поле спрашивается самим полем -- `record[details]`, а не строкой.
     * Kotlin типизирован, и опечатку здесь ловит компилятор.
     *
     * Считает ровно то же, что два других приложения: одинаковость тройки
     * проверяется прогоном, а не обещанием в README.
     */
    fun summary(self: Records) {
        for (record in self) {
            val words = Regex("\\s+").split(record.text(title)).filter { it.isNotEmpty() }
            // Заглавные расставляет **сторонняя** библиотека с Maven Central.
            // У Kotlin/Wasm её не бывает: тот принимает только пересобранные
            // под свою цель. Работает -- значит работает байткод JVM.
            val first = WordUtils.capitalizeFully(words.take(5).joinToString(" "))
            record[details] = "${words.size} слов: $first"
        }
    }
}

object Line : View("Line", model = Note) {
    override fun ui() = nodes(
        row(
            Note.done(widget = "toggle"),
            Note.title(widget = "title"),
            button(icon = "delete", action = Note.delete()),
        ),
    )
}

// Карточка -- работа, а не шаг пути: путь сюда весь состоит из списка,
// из которого пришли, и цепочка из двух звеньев повторила бы стрелку
// «назад» в том же баре.
object Card : View("Card", model = Note, crumbs = false) {
    override fun ui() = nodes(
        // Сохранение объявляется явно: карточка открывается черновиком, и до
        // сохранения записи ещё нет -- звать логику не о чем.
        button("Сохранить", action = Note.save(), place = "navbar", enabled = Note.title),
        Note.title(),
        Note.details(widget = "textarea"),
        Note.done(),
        // Метод модели прямо в кнопке. Ссылка идёт через `action("summary")`:
        // имя метода Kotlin отдаёт только отражением, а сослаться на сам
        // `Note::summary` нельзя -- кнопке нужно объявление, а не функция.
        button("Пересчитать сводку", action = Note.action("summary")),
    )
}

object Board : View("Board", title = "Заметки") {
    override fun ui() = nodes(
        list(Note, item = Line, open = Card, empty = listOf("Пусто", "Нажмите +, чтобы добавить")),
        button(place = "fab", action = Note.create(open = Card, draft = true)),
    )
}

val application = app(
    title = "Заметки (Kotlin)",
    // Обе, а не одна: `commons-text` опирается на `commons-lang3`, а сборка
    // транзитивные зависимости не разрешает намеренно -- разрешатель это
    // Maven, и полурабочая его копия однажды соврала бы о версии. Забудешь
    // вторую -- TeaVM скажет об этом прямо: «Class ... was not found».
    dependencies = listOf(
        "org.apache.commons:commons-text:1.12.0",
        "org.apache.commons:commons-lang3:3.14.0",
    ),
    color = "#45566b",
    locale = "ru",
    models = listOf(Note),
    views = listOf(Line, Card, Board),
    screens = listOf(Screen(Board)),
)
