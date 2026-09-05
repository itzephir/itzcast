package dev.itzcast.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.itzcast.core.ActionInteraction
import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionViewContext
import dev.itzcast.core.HotKey
import dev.itzcast.core.HotKeyKey
import dev.itzcast.core.HotKeyModifier
import dev.itzcast.core.ItzcastSettings
import dev.itzcast.core.LaunchContext
import dev.itzcast.core.Pipeline
import dev.itzcast.core.QueryContext
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import dev.itzcast.platform.BundledExtensionInstaller
import dev.itzcast.platform.DesktopHotKeyMapper
import dev.itzcast.platform.ExternalExtensionCatalog
import dev.itzcast.platform.MacApplicationActivator
import dev.itzcast.platform.MacGlobalHotKey
import dev.itzcast.platform.SettingsStore
import dev.itzcast.platform.desktopActions
import java.awt.Color as AwtColor
import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    val home = remember { Path.of(System.getProperty("user.home")) }
    val itzcastHome = remember { home.resolve(".itzcast") }
    val catalog = remember {
        BundledExtensionInstaller(itzcastHome).install()
        ExternalExtensionCatalog(itzcastHome)
    }
    val settingsStore = remember { SettingsStore(home.resolve(".itzcast/settings.toml")) }
    val actions = remember { desktopActions() }
    val pipeline = remember(catalog, actions) {
        Pipeline(
            extensions = catalog.load(),
            actions = actions,
        )
    }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var visible by remember { mutableStateOf(true) }
    var launchSequence by remember { mutableIntStateOf(1) }
    var inputFocusSequence by remember { mutableIntStateOf(0) }
    var windowHasAcquiredFocus by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(AppScreen.LAUNCHER) }
    var hotKeyRegistrationError by remember { mutableStateOf<String?>(null) }
    var windowKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    val hotKeyHolder = remember { HotKeyRegistrationHolder() }

    LaunchedEffect(pipeline) {
        pipeline.startup()
    }

    DisposableEffect(catalog) {
        onDispose { catalog.close() }
    }

    fun toggleLauncher() {
        EventQueue.invokeLater {
            if (visible) {
                visible = false
                windowHasAcquiredFocus = false
            } else {
                windowHasAcquiredFocus = false
                visible = true
                screen = AppScreen.LAUNCHER
                launchSequence++
            }
        }
    }

    fun handleGlobalHotKey() {
        MacApplicationActivator.activate()
        toggleLauncher()
    }

    DisposableEffect(Unit) {
        hotKeyHolder.registration = MacGlobalHotKey.register(settings.hotKey, ::handleGlobalHotKey)
        if (hotKeyHolder.registration == null) hotKeyRegistrationError = "Could not register ${settings.hotKey.displayName()}"
        onDispose { hotKeyHolder.registration?.close() }
    }

    fun saveHotKey(hotKey: HotKey): String? {
        if (hotKey == settings.hotKey) return null
        val replacement = MacGlobalHotKey.register(hotKey, ::handleGlobalHotKey)
            ?: return "Shortcut ${hotKey.displayName()} is already used by macOS or another app"
        val newSettings = ItzcastSettings(hotKey)
        val saved = settingsStore.save(newSettings)
        if (saved.isFailure) {
            replacement.close()
            return saved.exceptionOrNull()?.message ?: "Could not save settings"
        }
        hotKeyHolder.registration?.close()
        hotKeyHolder.registration = replacement
        settings = newSettings
        hotKeyRegistrationError = null
        return null
    }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.TopCenter),
        size = DpSize(720.dp, 520.dp),
    )

    Window(
        onCloseRequest = { visible = false },
        state = windowState,
        visible = visible,
        title = "itzcast",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        onPreviewKeyEvent = { event -> windowKeyHandler?.invoke(event) ?: false },
    ) {
        window.background = AwtColor(0, 0, 0, 0)
        DisposableEffect(window) {
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(event: WindowEvent) {
                    windowHasAcquiredFocus = true
                    inputFocusSequence++
                }

                override fun windowLostFocus(event: WindowEvent) {
                    if (visible && windowHasAcquiredFocus) {
                        windowHasAcquiredFocus = false
                        visible = false
                    }
                }
            }
            window.addWindowFocusListener(focusListener)
            onDispose { window.removeWindowFocusListener(focusListener) }
        }
        LaunchedEffect(visible, launchSequence) {
            if (visible) {
                MacApplicationActivator.activate()
                repeat(10) {
                    window.toFront()
                    window.requestFocus()
                    if (window.isFocused) return@LaunchedEffect
                    delay(25)
                }
            }
        }

        when (screen) {
            AppScreen.LAUNCHER -> Launcher(
                visible = visible,
                actionContext = { query -> ActionViewContext(launchSequence, query, visible && screen == AppScreen.LAUNCHER) },
                launchSequence = launchSequence,
                inputFocusSequence = inputFocusSequence,
                hotKey = settings.hotKey,
                pipeline = pipeline,
                dismiss = { visible = false },
                openSettings = { screen = AppScreen.SETTINGS },
            )
            AppScreen.SETTINGS -> SettingsScreen(
                currentHotKey = settings.hotKey,
                registrationError = hotKeyRegistrationError,
                onSave = ::saveHotKey,
                onClose = { screen = AppScreen.LAUNCHER },
                installKeyHandler = { windowKeyHandler = it },
            )
        }
    }
}

private enum class AppScreen { LAUNCHER, SETTINGS }

private class HotKeyRegistrationHolder(var registration: MacGlobalHotKey? = null)

@Composable
private fun Launcher(
    visible: Boolean,
    actionContext: (String) -> ActionViewContext,
    launchSequence: Int,
    inputFocusSequence: Int,
    hotKey: HotKey,
    pipeline: Pipeline,
    dismiss: () -> Unit,
    openSettings: () -> Unit,
) {
    var queryState by remember { mutableStateOf<LauncherQueryState>(LauncherQueryState.Plain()) }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var launchContext by remember { mutableStateOf(LaunchContext()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val suggestionListState = rememberLazyListState()
    val interaction = remember { ActionInteraction() }
    var refreshSequence by remember { mutableIntStateOf(0) }
    SideEffect { interaction.update(ActionViewContext(launchSequence, queryState.query, visible)) }

    fun updateQuery(state: LauncherQueryState) {
        if (queryState.prefixActive != state.prefixActive) {
            suggestions = emptyList()
            selectedIndex = 0
        }
        queryState = state
        interaction.update(actionContext(state.query))
    }

    fun select(index: Int) {
        if (suggestions.isNotEmpty()) selectedIndex = index.coerceIn(suggestions.indices)
    }

    fun useSelected() {
        val suggestion = suggestions.getOrNull(selectedIndex) ?: return
        val view = actionContext(queryState.query)
        val ticket = interaction.begin(view) ?: return
        status = null
        scope.launch {
            try {
                val result = pipeline.use(view.query, suggestion)
                val completion = interaction.finish(ticket, result, actionContext(queryState.query)) ?: return@launch
                if (completion.error != null) {
                    status = completion.error
                } else when (completion.outcome) {
                    ActionOutcome.CLOSE -> {
                        updateQuery(LauncherQueryState.Plain())
                        suggestions = emptyList()
                        dismiss()
                    }
                    ActionOutcome.KEEP_OPEN -> Unit
                    ActionOutcome.REFRESH -> refreshSequence++
                    null -> Unit
                }
            } finally {
                interaction.abandon(ticket)
            }
        }
    }

    LaunchedEffect(launchSequence) {
        launchContext = pipeline.launch()
        updateQuery(LauncherQueryState.Plain())
        suggestions = emptyList()
        selectedIndex = 0
        status = null
    }

    LaunchedEffect(queryState.query, queryState.prefixActive, launchContext, pipeline, refreshSequence) {
        val state = queryState
        delay(45)
        val results = pipeline.suggest(
            QueryContext(state.query, launchContext),
            includePrefixSuggestions = state.prefixActive,
        )
        if (state.query != queryState.query || state.prefixActive != queryState.prefixActive) {
            return@LaunchedEffect
        }
        suggestions = results
        selectedIndex = 0
    }

    LaunchedEffect(selectedIndex, suggestions) {
        if (suggestions.isEmpty()) return@LaunchedEffect
        val layout = suggestionListState.layoutInfo
        val visibleItems = layout.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val selectedItem = visibleItems.firstOrNull { it.index == selectedIndex }
        when {
            selectedItem == null && selectedIndex < visibleItems.first().index ->
                suggestionListState.animateScrollToItem(selectedIndex)

            selectedItem == null && selectedIndex > visibleItems.last().index -> {
                val viewportSize = layout.viewportEndOffset - layout.viewportStartOffset
                val estimatedItemSize = visibleItems.last().size
                val bottomAlignedOffset = -(viewportSize - estimatedItemSize).coerceAtLeast(0)
                suggestionListState.animateScrollToItem(selectedIndex, bottomAlignedOffset)
            }

            selectedItem != null && selectedItem.offset < layout.viewportStartOffset ->
                suggestionListState.animateScrollToItem(selectedIndex)

            selectedItem != null && selectedItem.offset + selectedItem.size > layout.viewportEndOffset -> {
                val viewportSize = layout.viewportEndOffset - layout.viewportStartOffset
                val bottomAlignedOffset = -(viewportSize - selectedItem.size).coerceAtLeast(0)
                suggestionListState.animateScrollToItem(selectedIndex, bottomAlignedOffset)
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 10.dp, end = 10.dp, bottom = 10.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Comma -> if (event.isMetaPressed) { openSettings(); true } else false
                        Key.DirectionDown -> { select(selectedIndex + 1); true }
                        Key.DirectionUp -> { select(selectedIndex - 1); true }
                        Key.Enter -> { useSelected(); true }
                        Key.Escape -> { dismiss(); true }
                        else -> false
                    }
                },
            color = Palette.panel,
            shape = RoundedCornerShape(18.dp),
            elevation = 18.dp,
        ) {
            Column {
                SearchField(
                    state = queryState,
                    focusKey = inputFocusSequence,
                    onPlainValueChanged = { plainState, value ->
                        updateQuery(plainState.update(value, pipeline.matchPrefix(value.text)))
                    },
                    onStateChanged = ::updateQuery,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
                if (suggestions.isEmpty()) {
                    EmptyState(queryState.query, Modifier.fillMaxWidth().weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                        state = suggestionListState,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        itemsIndexed(suggestions, key = { _, item -> item.id }) { index, item ->
                            SuggestionRow(
                                suggestion = item,
                                selected = index == selectedIndex,
                                onClick = { selectedIndex = index; useSelected() },
                            )
                        }
                    }
                }
                Footer(status, hotKey, openSettings)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentHotKey: HotKey,
    registrationError: String?,
    onSave: (HotKey) -> String?,
    onClose: () -> Unit,
    installKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit,
) {
    var draft by remember(currentHotKey) { mutableStateOf(currentHotKey) }
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(registrationError) }
    val currentKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else if (!recording) {
            if (event.key == Key.Escape) { onClose(); true } else false
        } else {
            val captured = DesktopHotKeyMapper.fromAwtKeyCode(
                keyCode = event.key.keyCode.toInt(),
                isMetaPressed = event.isMetaPressed,
                isAltPressed = event.isAltPressed,
                isShiftPressed = event.isShiftPressed,
                isCtrlPressed = event.isCtrlPressed,
            )
            if (captured != null) {
                draft = captured
                recording = false
                message = null
            } else {
                message = "Hold at least one modifier, then press a letter, number, Space, Enter, or F-key"
            }
            true
        }
    }

    DisposableEffect(Unit) {
        installKeyHandler { event -> currentKeyHandler(event) }
        onDispose { installKeyHandler(null) }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 10.dp, end = 10.dp, bottom = 10.dp),
            color = Palette.panel,
            shape = RoundedCornerShape(18.dp),
            elevation = 18.dp,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("‹", color = Palette.accent, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onClose))
                    Spacer(Modifier.size(14.dp))
                    Text("Settings", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "v${System.getProperty("itzcast.version", "dev")}",
                        color = Palette.muted,
                        fontSize = 11.sp,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
                Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp)) {
                    Text("Global hotkey", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "This shortcut shows or hides itzcast from any application.",
                        color = Palette.muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            recording = true
                            message = null
                        },
                        color = if (recording) Palette.selected else Palette.icon,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (recording) "Press a new shortcut…" else "Current shortcut",
                                    color = Palette.muted,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (recording) "Hold modifier + key" else draft.displayName(),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(if (recording) "Recording" else "Change", color = Palette.accent, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Presets", color = Palette.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShortcutPreset(
                            hotKey = HotKey.DEFAULT,
                            selected = draft == HotKey.DEFAULT,
                            onClick = { draft = HotKey.DEFAULT; recording = false; message = null },
                        )
                        ShortcutPreset(
                            hotKey = HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.ENTER),
                            selected = draft == HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.ENTER),
                            onClick = {
                                draft = HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.ENTER)
                                recording = false
                                message = null
                            },
                        )
                        ShortcutPreset(
                            hotKey = HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.SPACE),
                            selected = draft == HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.SPACE),
                            onClick = {
                                draft = HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.SPACE)
                                recording = false
                                message = null
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "To use ⌘Space, first disable or change the Spotlight shortcut in macOS System Settings.",
                        color = Palette.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Independent project", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "itzcast is not affiliated with or endorsed by Apple or Raycast. " +
                            "Apple, macOS, and Spotlight are trademarks of Apple Inc. " +
                            "Raycast is a trademark of Raycast Technologies Ltd.",
                        color = Palette.muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    message?.let {
                        Text(it, color = Palette.error, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SettingsAction("Cancel", primary = false, onClick = onClose)
                        Spacer(Modifier.size(8.dp))
                        SettingsAction("Save", primary = true) {
                            message = onSave(draft)
                            if (message == null) onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutPreset(hotKey: HotKey, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Palette.selected else Palette.icon,
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(
            hotKey.displayName(),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) Palette.accent else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (primary) Palette.accent else Palette.icon,
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (primary) Palette.panel else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchField(
    state: LauncherQueryState,
    focusKey: Int,
    onPlainValueChanged: (LauncherQueryState.Plain, TextFieldValue) -> Unit,
    onStateChanged: (LauncherQueryState) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusKey) {
        delay(50)
        focusRequester.requestFocus()
    }
    LaunchedEffect(state is LauncherQueryState.Prefixed) {
        focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = Palette.accent, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.size(15.dp))
        when (state) {
            is LauncherQueryState.Plain -> BasicTextField(
                value = state.value,
                onValueChange = { onPlainValueChanged(state, it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.accent),
                decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) {
                            Text("Search apps or type a command…", color = Palette.muted, fontSize = 24.sp)
                        }
                        inner()
                    }
                },
            )

            is LauncherQueryState.Prefixed -> {
                Surface(
                    color = Color.Transparent,
                    contentColor = Palette.accent,
                    shape = MaterialTheme.shapes.small,
                    border = ButtonDefaults.outlinedBorder,
                ) {
                    Text(
                        state.prefix,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.size(10.dp))
                BasicTextField(
                    value = state.arguments,
                    onValueChange = { onStateChanged(state.copy(arguments = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            val atStart = state.arguments.selection.start == 0 &&
                                state.arguments.selection.end == 0
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                                onStateChanged(state.removePrefix())
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.accent),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(query: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            if (query.isBlank()) "Try “yt cats”, “bash ls -la”, or “12 * (4 + 3)”" else "Searching…",
            color = Palette.muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Palette.selected else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Palette.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text(kindGlyph(suggestion.kind), color = Palette.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            Text(suggestion.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            suggestion.subtitle?.let { Text(it, color = Palette.muted, fontSize = 12.sp, maxLines = 1) }
        }
        if (selected) Text("↵", color = Palette.muted, fontSize = 14.sp)
    }
}

@Composable
private fun Footer(status: String?, hotKey: HotKey, openSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(status ?: "itzcast", color = if (status == null) Palette.muted else Palette.error, fontSize = 11.sp)
        Spacer(Modifier.size(12.dp))
        Text(
            "${hotKey.displayName()} to toggle  ·  ↑↓ navigate  ·  esc close",
            color = Palette.muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(10.dp))
        Surface(
            modifier = Modifier.clickable(onClick = openSettings),
            color = Palette.icon,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                "⚙ Settings",
                color = Palette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun kindGlyph(kind: SuggestionKind): String = when (kind) {
    SuggestionKind.APPLICATION -> "A"
    SuggestionKind.COMMAND -> ">_"
    SuggestionKind.CALCULATION -> "="
    SuggestionKind.WEB -> "↗"
    SuggestionKind.CUSTOM -> "✦"
}

private object Palette {
    val panel = Color(0xF5222227)
    val border = Color(0xFF3A3A43)
    val selected = Color(0xFF393946)
    val icon = Color(0xFF30303A)
    val accent = Color(0xFFB6A3FF)
    val muted = Color(0xFF9696A2)
    val error = Color(0xFFFF8A8A)
}
