# itzcast

[English](README.md) | Русский

itzcast — быстрый и расширяемый launcher для macOS в духе Spotlight и Raycast.
Проект построен на Kotlin Multiplatform и Compose Desktop; UI, алгоритмы
pipeline, платформенная интеграция и расширения не зависят друг от друга.

Текущая версия приложения: **0.0.1**.

## Что уже работает

- настраиваемый глобальный хоткей показывает и скрывает окно; по умолчанию —
  `⌥ Space`;
- экран Settings записывает новое сочетание и сохраняет его в
  `~/.itzcast/settings.toml`;
- fuzzy-поиск по приложениям из `/Applications`, `/System/Applications` и
  `~/Applications`;
- Enter запускает выбранное приложение, а обычный запрос открывает поиск в
  браузере;
- `bash ls -la` и `sh …` запускают команду через zsh в домашнем каталоге;
- `yt смешное видео с котиками` открывает поиск YouTube;
- математические выражения вроде `2 + 3 * (4 ^ 2)` вычисляются локально, Enter
  копирует результат;
- официальный каталог `applications`, `bash`, `calculator`, `youtube` и
  `web-search` устанавливается в `~/.itzcast/extensions` как настоящие process
  extensions;
- пользовательские расширения из `~/.itzcast/extensions/*/manifest.toml`
  загружаются тем же механизмом;
- хуки `launch`, `prefix`, `suggest` и `use` работают через языконезависимый
  JSON Lines protocol.

## Запуск

Требования: macOS и JDK 21+.

```bash
./gradlew :app:run
```

DMG можно собрать так:

```bash
./gradlew --no-configuration-cache :app:packagePublicDmg
```

Release-артефакт появится в `app/build/release/itzcast-0.0.1.dmg`.

При первом старте окно показывается сразу. Затем `⌥ Space` работает глобально
через Carbon Hot Key API и не требует Accessibility permission. Чтобы изменить
сочетание, откройте itzcast, нажмите `⚙ Settings`, затем `Change`, введите новое
сочетание и сохраните его.

### Как использовать `⌘ Space`

macOS по умолчанию назначает `⌘ Space` на Spotlight, поэтому сначала нужно
освободить сочетание:

1. Откройте **Apple menu → System Settings → Keyboard → Keyboard Shortcuts**.
2. Выберите **Spotlight** в боковой панели.
3. Отключите shortcut открытия Spotlight либо дважды нажмите на текущее
   сочетание и назначьте Spotlight другое.
4. Вернитесь в itzcast: **⚙ Settings → Change**, нажмите `⌘ Space`, затем
   **Save**.

Если сочетание всё ещё занято macOS или другим приложением, itzcast покажет
ошибку и сохранит прежний рабочий hotkey. Инструкция по изменению и отключению
системных shortcuts есть также в
[Apple Support](https://support.apple.com/guide/mac-help/keyboard-shortcuts-mchlp2262/mac).

## Архитектура

```text
app (Compose UI + composition root)
  ├── core (KMP: модели, контракты, ranking, pipeline)
  ├── platform-desktop (actions, hotkey, extension host)
  └── extensions (официальный Kotlin/JVM-каталог + TOML manifests)
```

- `core` не знает о Compose, файловой системе, браузере или процессах. Все side
  effects проходят через `ActionExecutor`.
- `platform-desktop` реализует macOS-адаптеры и host protocol внешних
  расширений.
- `app` создаёт pipeline и отображает его состояние. UI можно заменить, не
  переписывая поиск и расширения.
- В host нет конкретных реализаций extensions: даже `bash` работает через
  отдельный TOML-манифест и process protocol.
- Официальные и пользовательские suggestions проходят один pipeline и имеют
  стабильный `sourceId` — это задел под динамический ranking по `use`-событиям.
- Ошибка одного extension hook изолируется и не ломает выдачу остальных.

Подробный контракт расширений: [docs/extensions.md](docs/extensions.md).
Готовый Python-пример:
[examples/extensions/github-search](examples/extensions/github-search).

## Проверка

```bash
./gradlew check :app:compileKotlin
```

GitHub Actions прогоняет проверки на macOS и Linux; macOS job дополнительно
собирает DMG и сохраняет его как artifact.

## Ближайшие архитектурные шаги

1. Инкрементальный индекс файлов и Spotlight metadata adapter.
2. Версионирование/capabilities extension protocol и permission manifest.
3. История выбора и динамический ranking на основе `use`-событий и `sourceId`.
4. Rich suggestions: иконки, формы, preview, secondary actions и streaming
   updates.
5. Персистентные процессы расширений с backpressure вместо process-per-hook.
6. Общий UI state/controller в `commonMain` и платформенные реализации для
   Windows/Linux.

## Безопасность

Расширения — доверенный пользовательский код: executable из
`~/.itzcast/extensions` запускается с правами пользователя. Устанавливайте
только проверенные расширения. Команда `bash` выполняет введённую строку
намеренно и без sandbox.

## Участие и лицензия

itzcast распространяется по [Apache License 2.0](LICENSE). Она разрешает
использование, изменение и распространение кода, сохраняет уведомления об
авторстве и включает явный патентный грант.

itzcast — независимый проект, не связанный с Apple Inc. или Raycast
Technologies Ltd. и не одобренный ими. Apple, macOS и Spotlight являются
товарными знаками Apple Inc. Raycast является товарным знаком Raycast
Technologies Ltd. Упоминания этих продуктов используются только для описания
совместимости, сочетаний клавиш и функционального контекста. Полный текст
уведомления находится в [NOTICE](NOTICE).

Для pull request нужны две вещи: однократное принятие [CLA](CLA.md) и DCO
sign-off в каждом коммите. Инструкции находятся в
[CONTRIBUTING.md](CONTRIBUTING.md), полный текст DCO — в [DCO](DCO).

Ветка `main` принимает только криптографически подписанные коммиты со статусом
GitHub `Verified`. Если в Git включён `commit.gpgsign=true`, команда
`git commit -s` одновременно создаёт криптографическую подпись и добавляет
необходимый DCO trailer `Signed-off-by`.
