# Extension protocol

itzcast ищет расширения в `~/.itzcast/extensions/<extension-id>/manifest.toml`. Каталог и подпапка `extensions` создаются автоматически. Каталог расширений и actions загружается при запуске приложения; после изменений перезапустите itzcast. JSON-манифесты не загружаются.

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

Конфигурация хранится только в TOML. JSON здесь используется исключительно как несохраняемый transport protocol между itzcast и процессом расширения: itzcast сохраняет процесс между запросами. На каждый запрос он записывает один JSON object и newline в stdin и ожидает одну строку JSON в stdout. Обрабатывайте stdin в цикле и сбрасывайте буфер stdout после каждого ответа. Запросы к одному процессу выполняются последовательно. Диагностику пишите в stderr. Пустая строка ответа эквивалентна `{}`; EOF считается ошибкой. После timeout или ошибки сессия закрывается, следующий запрос создаёт новую.

Общее поле `type` определяет hook:

- `startup`: `{ "type": "startup" }` — фоновая подготовка при запуске приложения; после восстановления процесса запрос повторяется.
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
      "id": "itzcast/openUrl",
      "payload": {"url": "https://example.com/search?q=query"}
    }
  }]
}
```

`kind`: `APPLICATION`, `COMMAND`, `CALCULATION`, `WEB`, `CUSTOM`.

## Actions

У каждого suggestion одно поле `action`: объект с полным `id` и **необязательным** `payload` (JSON object). Отсутствующий payload означает `{}`; `null`, массивы и примитивы не допускаются.

Все действия находятся в одном реестре. Встроенные обработчики зарегистрированы заранее:

```json
{"id":"itzcast/openUrl","payload":{"url":"https://example.com"}}
{"id":"itzcast/openPath","payload":{"path":"/Applications/Safari.app"}}
{"id":"itzcast/copy","payload":{"text":"42"}}
{"id":"itzcast/none"}
```

Запуск процесса без shell interpolation:

```json
{
  "id":"itzcast/command",
  "payload": {
    "command":["/usr/bin/say","Hello"],
    "workingDirectory":"/tmp",
    "environment":{"MODE":"demo"}
  }
}
```

`workingDirectory` и `environment` необязательны. Успех `openPath` и `command` означает запуск процесса, а не ожидание его завершения. Встроенные действия возвращают `close`.

### Объявление кастомных actions

Расширение объявляет локальные имена в своём `manifest.toml`:

```toml
id = "example.counter"
command = ["./extension.py"]
hooks = ["prefix"]
prefixes = ["count"]

[[actions]]
id = "increment"
```

В реестре появится `example.counter/increment`. Наличие `[[actions]]` автоматически подключает обработчик `execute`; добавлять его в `hooks` не нужно. Расширение также может предоставлять только actions, без hooks.

Полный ID имеет форму `<extension-id>/<local-action-id>`. Обе части непустые, без `/` и пробелов по краям. Пространство `itzcast/` зарезервировано. Некорректные объявления и повторяющиеся локальные ID исключают расширение из загрузки; при совпадении ID нескольких расширений исключаются все конфликтующие провайдеры.

Любое расширение может использовать зарегистрированное действие другого расширения:

```json
{"id":"example.counter/increment"}
```

Неизвестное действие, отключённый/отсутствующий провайдер или некорректный action исключают только соответствующий suggestion. Проверка происходит до объединения дубликатов, сортировки и ограничения количества результатов. Остальные suggestions в ответе сохраняются. Параметры конкретного действия проверяет его обработчик при выполнении.

### Выполнение

При выборе suggestion itzcast ищет действие в реестре и отправляет владельцу action запрос. `id` содержит **полный** идентификатор; `suggestion.sourceId` остаётся ID расширения, которое создало suggestion. `payload` может отсутствовать и в запросе `execute`.

```json
{
  "type":"execute",
  "id":"example.counter/increment",
  "query":"count",
  "suggestion": {
    "id":"example.counter:value",
    "title":"Counter: 0",
    "sourceId":"example.counter",
    "action":{"id":"example.counter/increment"}
  }
}
```

Ответ должен содержать `actionResult` с обязательным `succeeded`:

```json
{"actionResult":{"succeeded":true,"outcome":"refresh"}}
{"actionResult":{"succeeded":false,"error":"Could not increment"}}
```

Успешные исходы:

- `close` (по умолчанию) — очистить запрос и закрыть окно;
- `keepOpen` — сохранить запрос и результаты;
- `refresh` — сохранить запрос и повторить генерацию suggestions.

При ошибке окно остаётся открытым с сообщением. `{}`, отсутствующий результат, некорректный ответ и timeout считаются ошибкой. Для выполнения используется `timeoutMs` провайдера. `execute` автоматически не повторяется, даже при ошибке записи: действие могло успеть изменить внешнее состояние. Следующее явное действие пользователя может создать новую сессию. Не полагайтесь на сохранение состояния процесса после сбоя.

До завершения действия повторная активация блокируется. Ответ от предыдущего запроса или предыдущего открытия окна не изменяет текущий UI. Глобальное правило скрытия при потере фокуса продолжает действовать.

### Breaking change

Старые action objects с `type` больше не поддерживаются. Обновите свои расширения на `id` + необязательный `payload`. Адаптера совместимости и aliases нет; саджест со старым action будет пропущен.

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

## Пример кастомного action

```bash
cp -R examples/extensions/counter ~/.itzcast/extensions/
chmod +x ~/.itzcast/extensions/counter/extension.py
```

Требуется Python 3. Перезапустите itzcast и введите `count`. Enter увеличивает счётчик и обновляет результат, сохраняя окно. Счётчик хранится в памяти процесса и сбрасывается при его перезапуске. Пример не записывает конфигурацию или пользовательские данные.
