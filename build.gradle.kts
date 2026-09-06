// Сборка библиотеки объявления на Kotlin.
//
// Ядру этот файл не нужен: оно зовёт `kotlinc` по исходникам напрямую -- тот же
// файл приложения собирается дважды, под JVM (напечатать пакет объявления) и
// под WebAssembly через TeaVM (исполнить логику на устройстве). Файл здесь для
// **человека**: без него библиотеку нельзя ни собрать отдельно, ни подключить
// как зависимость. До 06.09.2026 её и нельзя было -- ни того, ни другого.
//
// Раскладка исходников не менялась и меняться не должна: ядро ищет их по
// `main/kotlin` и `jvmMain/kotlin` (`libs/js/src/build/kotlin.mjs`). Переложи
// их «как принято у Gradle» -- и сборка приложений на Kotlin перестанет
// находить библиотеку. Поэтому исходные каталоги названы здесь явно.

plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

group = "io.github.vlad-anisov"
version = "0.1.0"

repositories { mavenCentral() }

// Не `build/`, а папка с точки. В этом дереве `build/` уже занято смыслом --
// `libs/js/src/build/` -- и правило `.gitignore` для мусора сборки однажды
// заглушило его целиком. Точка в начале снимает и это, и вопрос сторожа
// `tests/together/test_ignored_sources.py`: папки с точки он не смотрит.
layout.buildDirectory.set(file(".gradle-build"))

dependencies {
    // Отражением `Declare.kt` находит действия модели: метод модели -- это уже
    // действие, и узнать их иначе нельзя.
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.setSrcDirs(listOf("src/main/kotlin", "src/jvmMain/kotlin"))
    // Образец `ParityApp.kt` лежит в `tests/fixtures` -- в дереве это два
    // шага вверх, в своём репозитории он рядом. Названы обе раскладки: счёт
    // «сколько шагов» разный, и написанный одним числом он разойдётся.
    val образцы = listOf(file("../../tests/fixtures"), file("tests/fixtures"))
        .firstOrNull { it.isDirectory } ?: error("tests/fixtures не найден")
    sourceSets["test"].kotlin.setSrcDirs(listOf("src/test/kotlin", образцы))
}

// Ресурсов у библиотеки нет: таблица типов едет **исходником**
// (`FieldTypes.kt`), потому что ресурс читает только JVM, а этот же код
// собирается ещё и под WebAssembly, где classpath нет.
sourceSets["main"].resources.setSrcDirs(emptyList<String>())
sourceSets["test"].resources.setSrcDirs(emptyList<String>())

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("oneframework-kotlin")
                description.set("Объявление приложения на Kotlin для One Framework")
                url.set("https://github.com/vlad-anisov/oneframework-kotlin")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
