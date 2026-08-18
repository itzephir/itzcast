# AGENTS.md

## Project

itzcast is an extensible macOS launcher inspired by Spotlight and Raycast. The public application version is currently `0.0.1` and is defined once in `gradle.properties`.

## Architecture

- `core`: Kotlin Multiplatform models, extension contracts, ranking primitives, and the side-effect-free pipeline.
- `platform-desktop`: macOS hotkey/window integration, actions, TOML settings, extension discovery, installation, and process hosting.
- `extensions`: the complete official extension catalog and its isolated Kotlin/JVM runtime.
- `app`: Compose Desktop UI and composition root. Keep feature logic out of this module.

UI, pipeline algorithms, platform adapters, and extension implementations must remain independently replaceable.

## Extension rules

- Do not add concrete extensions directly to `app`, `core`, or `platform-desktop`.
- Official extensions belong under `extensions/catalog/<extension-id>/manifest.toml`, with implementation code in the `extensions` module.
- Official and third-party extensions must use the same public hook and JSON Lines process protocol.
- Keep extension and suggestion `sourceId` values stable; ranking/history will learn from `use` events.
- Bundled extensions are installed under `~/.itzcast/extensions`; user-created extension directories must not be overwritten.
- `@java` in `command[0]` means the JVM bundled with itzcast.

## Configuration

- Persist configuration only as TOML.
- Settings live at `~/.itzcast/settings.toml`; extension metadata lives in `manifest.toml`.
- JSON is permitted only as the ephemeral stdin/stdout extension transport. Never save JSON configuration files.

## macOS behavior

- The global hotkey must activate itzcast as the foreground app and focus the search input.
- Losing window focus must hide itzcast.
- Keyboard selection must keep the active suggestion visible.
- The default shortcut is `⌥ Space`; `⌘ Space` requires releasing Spotlight's system shortcut first.

## Development

- Use JDK 21 or newer.
- Run checks with `./gradlew check :app:compileKotlin`.
- Build the public DMG with `./gradlew --no-configuration-cache :app:packagePublicDmg`.
- The release artifact is `app/build/release/itzcast-0.0.1.dmg`.
- Add unit tests for isolated logic and an end-to-end test when changing extension packaging or process transport.
- Preserve multiplatform boundaries and avoid macOS/JVM APIs in `core/commonMain`.
- Sign off every new commit under DCO 1.1 with `git commit -s`.

## Repository hygiene

- Do not commit build output, DMGs, IDE state, local settings, credentials, or files from `~/.itzcast`.
- Keep GitHub Actions green on both macOS and Linux.
- The project is licensed under Apache-2.0. External contributors must accept `CLA.md`, and every pull-request commit must pass the DCO check.
