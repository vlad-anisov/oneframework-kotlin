# oneframework-kotlin

Объявление приложения на Kotlin. Работает поверх
[ядра](https://github.com/vlad-anisov/oneframework).

```kotlin
object Note : Model("Note", label = "Заметка") {
    val title by string("Текст", required = true)
    val done by boolean("Выполнено")

    fun summary(self: Records) {          // метод модели — уже действие
        for (record in self) { /* ... */ }
    }
}
```

```bash
git clone https://github.com/vlad-anisov/oneframework.git ../oneframework
cd ../oneframework && npm install && cd -
node ../oneframework/src/build/cli.mjs web examples/notes-kotlin/App.kt
```

Тот же файл собирается дважды и разными компиляторами: **под JVM** — чтобы
напечатать пакет объявления на машине разработчика, **под WebAssembly** — чтобы
логика работала на устройстве. Отражение живёт только в первой половине, и файл
приложения от него не зависит.

Ядро находит эту библиотеку рядом (`../oneframework-kotlin/src`) либо по
`ONEFRAMEWORK_KOTLIN`.

## Что умеет и чего пока нет

Умеет своими типами: модели, поля, виды, действия, ссылки, сравнения,
связки «и»/«или»/«не».

Пока не умеет: узлы вида, кроме строки, поля, списка и кнопки — четыре из
девятнадцати; и семь родов узлов выражения из четырнадцати. Недостающее пишется
**строкой** — `expr("length(record.title) > 3")`, — дерево из неё собирает ядро.

## Что нужно на машине сборки

`node` и компилятор Kotlin (`brew install kotlin`). На устройство не едет ни то
ни другое.

## Лицензия

MIT.
