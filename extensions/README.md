# Official extensions

Здесь находится весь официальный каталог itzcast: applications, bash, calculator, counter, YouTube и web search. В host-приложении нет конкретных реализаций extensions.

- `catalog/` — настоящие каталоги расширений с `manifest.toml`;
- `src/main/` — независимый Kotlin/JVM process runtime;
- `src/test/` — тесты поведения расширений;
- `bundledExtensionArchive` — собирает каталог и runtime в архив для приложения.

При запуске приложение разворачивает архив в `~/.itzcast/extensions`. Расширения загружаются тем же `ExternalExtensionCatalog` и вызываются тем же JSON Lines protocol, что и пользовательские плагины. Процесс расширения может оставаться активным между запросами, а hook `startup` позволяет один раз за процесс itzcast запустить фоновую подготовку данных. Идентификаторы `sourceId` стабильны, поэтому поверх `use`-событий можно будет добавить историю и динамический ranking без специальных веток для официального каталога.

Демо-расширение `itzcast.counter` входит в поставку: запрос `count` показывает счётчик, Enter выполняет `itzcast.counter/increment` без payload и возвращает `refresh`. Значение хранится только в памяти процесса и сбрасывается при его перезапуске. Реализация использует тот же manifest и публичный протокол actions, что и сторонние расширения.
