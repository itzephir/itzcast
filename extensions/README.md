# Official extensions

Здесь находится весь официальный каталог itzcast: applications, bash, calculator, YouTube и web search. В host-приложении нет конкретных реализаций extensions.

- `catalog/` — настоящие каталоги расширений с `manifest.toml`;
- `src/main/` — независимый Kotlin/JVM process runtime;
- `src/test/` — тесты поведения расширений;
- `bundledExtensionArchive` — собирает каталог и runtime в архив для приложения.

При запуске приложение разворачивает архив в `~/.itzcast/extensions`. Расширения загружаются тем же `ExternalExtensionCatalog` и вызываются тем же JSON Lines protocol, что и пользовательские плагины. Идентификаторы `sourceId` стабильны, поэтому поверх `use`-событий можно будет добавить историю и динамический ranking без специальных веток для официального каталога.
