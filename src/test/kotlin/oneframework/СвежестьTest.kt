package oneframework

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Закреплённый пакет обязан совпадать с тем, что печатает библиотека сегодня.
 *
 * Единственная проверка, которая живёт **здесь**, а не в ядре: обе её стороны
 * лежат рядом -- образец и его закреплённый пакет. Всё прочее, что сторожит
 * Kotlin (сборка под TeaVM, переходник, поиск библиотеки), требует ядра и
 * живёт там. До 06.09.2026 эту библиотеку в расколотом виде не проверяло
 * ничто, и собрать её отдельно тоже было нечем.
 *
 * Сверяется богатый образец: он задевает каждый род узла договора, и потому
 * ловит то, чего простое приложение не касается.
 */
class СвежестьTest {

    /** Пакет, объявленный образцом, -- тем же кодом, каким его печатает `emit`. */
    private fun живой(): String {
        val приложение = parity.application
        discoverActions(приложение)
        return Json.write(declare(приложение))
    }

    @Test
    fun `закреплённый пакет parity не отстал от привязки`() {
        val закреплённый = ОБРАЗЦЫ.resolve("parity.bundle.json").readText()
        // Через разбор, а не построчно: закреплённый пакет напечатан
        // JavaScript, и отступы у двух печатей разные. Сверяется содержимое.
        assertEquals(нормально(закреплённый), нормально(живой()),
                     "tests/fixtures/parity.bundle.json отстал: пересоберите его")
    }

    @Test
    fun `образец вправду что-то объявляет`() {
        // Иначе сверка зеленела бы на пустом пакете.
        val пакет = живой()
        assertTrue(пакет.contains("\"Полка\""), "в пакете нет моделей образца")
        assertTrue(пакет.length > 1000, "пакет подозрительно короток: ${пакет.length}")
    }

    private companion object {
        /** Образцы лежат в `tests/fixtures` -- и в дереве, и в своём репозитории. */
        val ОБРАЗЦЫ: File = listOf(
            File("../../tests/fixtures"), File("tests/fixtures"),
        ).firstOrNull { it.isDirectory }
            ?: error("tests/fixtures не найден: искали ../../tests/fixtures и tests/fixtures")

        /** Убрать пробелы между лексемами JSON, не тронув их внутри строк. */
        fun нормально(текст: String): String {
            val из = StringBuilder(текст.length)
            var вСтроке = false
            var экран = false
            for (с in текст) {
                when {
                    экран -> экран = false
                    вСтроке && с == '\\' -> экран = true
                    с == '"' -> вСтроке = !вСтроке
                }
                if (вСтроке || !с.isWhitespace()) из.append(с)
            }
            return из.toString()
        }
    }
}
