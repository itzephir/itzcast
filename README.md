# itzcast

English | [Русский](README.ru.md)

itzcast is a fast, extensible launcher for macOS inspired by Spotlight and
Raycast. It is built with Kotlin Multiplatform and Compose Desktop, with clear
boundaries between the UI, pipeline algorithms, platform integration, and
extensions.

Current application version: **0.0.1**.

## What works today

- A configurable global hotkey shows and hides the launcher. The default is
  `⌥ Space`.
- The Settings screen records a new hotkey and stores it in
  `~/.itzcast/settings.toml`.
- Fuzzy application search covers `/Applications`, `/System/Applications`, and
  `~/Applications`.
- Enter launches the selected application; a plain query opens a web search in
  the default browser.
- `bash ls -la` and `sh …` run a command through zsh in the user's home
  directory.
- `yt funny cat videos` opens a YouTube search.
- Expressions such as `2 + 3 * (4 ^ 2)` are evaluated locally; Enter copies
  the result.
- The official `applications`, `bash`, `calculator`, `youtube`, and
  `web-search` catalog is installed under `~/.itzcast/extensions` as real
  process extensions.
- User extensions from `~/.itzcast/extensions/*/manifest.toml` are loaded by
  the same mechanism.
- The `launch`, `prefix`, `suggest`, and `use` hooks use a language-independent
  JSON Lines protocol.

## Running the application

Requirements: macOS and JDK 21 or newer.

```bash
./gradlew :app:run
```

Build a DMG with:

```bash
./gradlew --no-configuration-cache :app:packagePublicDmg
```

The release artifact is written to
`app/build/release/itzcast-0.0.1.dmg`.

The launcher is visible immediately on its first run. After that, `⌥ Space`
works globally through the Carbon Hot Key API and does not require Accessibility
permission. To change the shortcut, open itzcast, select `⚙ Settings`, click
`Change`, press the new combination, and save it.

### Using `⌘ Space`

macOS assigns `⌘ Space` to Spotlight by default, so release that shortcut
first:

1. Open **Apple menu → System Settings → Keyboard → Keyboard Shortcuts**.
2. Select **Spotlight** in the sidebar.
3. Disable the Spotlight shortcut, or double-click it and assign another
   combination.
4. Return to itzcast, open **⚙ Settings → Change**, press `⌘ Space`, and select
   **Save**.

If macOS or another application still owns the combination, itzcast reports an
error and keeps the previous working hotkey. See
[Apple Support](https://support.apple.com/guide/mac-help/keyboard-shortcuts-mchlp2262/mac)
for the system shortcut instructions.

## Architecture

```text
app (Compose UI + composition root)
  ├── core (KMP: models, contracts, ranking, pipeline)
  ├── platform-desktop (actions, hotkey, extension host)
  └── extensions (official Kotlin/JVM catalog + TOML manifests)
```

- `core` has no knowledge of Compose, filesystems, browsers, or processes. All
  side effects go through `ActionRegistry`.
- `platform-desktop` implements the macOS adapters and the external extension
  host protocol.
- `app` creates the pipeline and renders its state. The UI can be replaced
  without rewriting search or extensions.
- The host contains no concrete extension implementations. Even `bash` is a
  separate TOML manifest using the process protocol.
- Official and user suggestions share one pipeline and have stable `sourceId`
  values, providing the foundation for dynamic ranking based on `use` events.
- A failing extension hook is isolated and cannot break results from other
  extensions.

The extension contract is documented in
[docs/extensions.md](docs/extensions.md). A ready-to-run Python example is
available under
[examples/extensions/github-search](examples/extensions/github-search).

## Verification

```bash
./gradlew check :app:compileKotlin
```

GitHub Actions runs checks on macOS and Linux. The macOS job also builds the
DMG and uploads it as an artifact.

## Architectural roadmap

1. Incremental file index and a Spotlight metadata adapter.
2. Versioned extension protocol, capabilities, and permission manifests.
3. Selection history and dynamic ranking based on `use` events and `sourceId`.
4. Rich suggestions: icons, forms, previews, secondary actions, and streaming
   updates.
5. Persistent extension processes with backpressure instead of one process per
   hook invocation.
6. Shared UI state and controllers in `commonMain`, with platform
   implementations for Windows and Linux.

## Security

Extensions are trusted user code: executables under `~/.itzcast/extensions`
run with the user's permissions. Install extensions only from sources you
trust. The `bash` command intentionally executes the supplied input without a
sandbox.

## Contributing and license

itzcast is distributed under the [Apache License 2.0](LICENSE). It permits use,
modification, and distribution, preserves attribution notices, and includes an
explicit patent grant.

itzcast is an independent project and is not affiliated with or endorsed by
Apple Inc. or Raycast Technologies Ltd. Apple, macOS, and Spotlight are
trademarks of Apple Inc. Raycast is a trademark of Raycast Technologies Ltd.
References to these products are used solely to describe compatibility,
keyboard shortcuts, and functional context. See [NOTICE](NOTICE) for the full
notice.

Pull requests require one-time acceptance of the [CLA](CLA.md) and a DCO
sign-off on every commit. See [CONTRIBUTING.md](CONTRIBUTING.md) for the full
workflow and [DCO](DCO) for the certificate text.

The `main` branch accepts only cryptographically signed commits that GitHub
marks as `Verified`. With `commit.gpgsign=true`, `git commit -s` both signs the
commit cryptographically and adds the required DCO `Signed-off-by` trailer.
