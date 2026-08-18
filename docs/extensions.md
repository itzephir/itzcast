# Extension protocol

itzcast ищет расширения в `~/.itzcast/extensions/<extension-id>/manifest.toml`. Каталог и подпапка `extensions` создаются автоматически. Изменения подхватываются при следующем открытии окна. JSON-манифесты не загружаются.

## Manifest

```toml
id = "example.github-search"
name = "GitHub Search"
version = "0.1.0"
enabled = true
command = ["./extension.py"]
hooks = ["launch", "prefix", "suggest", "use"]
prefixes = ["gh"]
priority = 70
timeoutMs = 2000

[environment]
MODE = "production"
```

`command[0]` разрешается относительно каталога расширения, если путь не абсолютный. Остальные элементы передаются как отдельные аргументы без shell interpolation.

Специальное значение `@java` в `command[0]` запускает JVM из runtime самого itzcast. Оно используется официальными Kotlin-расширениями и не требует установленной в системе Java.

## Official catalog

Исходники всех официальных расширений находятся в [`extensions/`](../extensions). При старте itzcast устанавливает их рядом с пользовательскими плагинами:

```text
~/.itzcast/extensions/
  .runtime/itzcast-extensions.jar
  itzcast.applications/manifest.toml
  itzcast.bash/manifest.toml
  itzcast.calculator/manifest.toml
  itzcast.youtube/manifest.toml
  itzcast.web-search/manifest.toml
```

Каталоги с префиксом `itzcast.` и `.runtime` управляются приложением и обновляются из поставляемого архива при запуске. Для собственной версии скопируйте каталог под новым `id`: пользовательские каталоги приложение не перезаписывает.

## Transport

Конфигурация хранится только в TOML. JSON здесь используется исключительно как несохраняемый transport protocol между itzcast и процессом расширения: на каждый hook itzcast запускает процесс, записывает один JSON object и newline в stdin, закрывает stdin и ожидает один JSON object в первой строке stdout. Диагностику нужно писать в stderr. Пустой stdout эквивалентен `{}`.

Общее поле `type` определяет hook:

- `launch`: `{ "type": "launch", "context": { "attributes": {} } }`
- `prefix`: `{ "type": "prefix", "context": { ... }, "match": { "prefix": "gh", "arguments": "kotlin" } }`
- `suggest`: `{ "type": "suggest", "context": { "query": "...", "launch": { ... } } }`
- `use`: `{ "type": "use", "event": { "phase": "BEFORE|AFTER", "query": "...", "suggestion": { ... } } }`

`launch` может вернуть изменённый контекст:

```json
{"launchContext":{"attributes":{"project":"itzcast"}}}
```

`prefix` и `suggest` возвращают suggestions:

```json
{
  "suggestions": [{
    "id": "example:query",
    "title": "Search example",
    "subtitle": "Optional subtitle",
    "score": 80.0,
    "kind": "WEB",
    "sourceId": "example.extension",
    "action": {
      "type": "openUrl",
      "url": "https://example.com/search?q=query"
    }
  }]
}
```

`kind`: `APPLICATION`, `COMMAND`, `CALCULATION`, `WEB`, `CUSTOM`.

## Actions

Open URL:

```json
{"type":"openUrl","url":"https://example.com"}
```

Open file/application:

```json
{"type":"openPath","path":"/Applications/Safari.app"}
```

Run a process without shell parsing:

```json
{
  "type":"command",
  "command":["/usr/bin/say","Hello"],
  "workingDirectory":"/tmp",
  "environment":{"MODE":"demo"}
}
```

Copy text:

```json
{"type":"copy","text":"42"}
```

No action:

```json
{"type":"none"}
```

## Hook semantics

- `launch` вызывается по priority от большего к меньшему. Результат каждого hook становится входом следующего.
- `prefix` вызывается при точном prefix или `prefix + space`; handler получает остаток строки в `arguments`.
- `suggest` вызывается для любого запроса и подходит для калькуляторов, поиска и контекстных подсказок.
- `use` получает две нотификации: `BEFORE` перед action и `AFTER` с `succeeded`/`error`. Он подходит для metrics, history и audit.
- Suggestions объединяются по `id`, затем сортируются по `score`. Верхние 12 отображаются в UI.
- Ошибка или timeout отдельного hook не прерывает pipeline.

## Установка примера

```bash
mkdir -p ~/.itzcast/extensions
cp -R examples/extensions/github-search ~/.itzcast/extensions/
chmod +x ~/.itzcast/extensions/github-search/extension.py
```

После этого запрос `gh compose multiplatform` предложит поиск GitHub.
