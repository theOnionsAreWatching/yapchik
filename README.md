# Softkey Integration

[![Build](https://github.com/theonionsarewatching/yapchik/actions/workflows/build.yml/badge.svg)](https://github.com/theonionsarewatching/yapchik/actions/workflows/build.yml)

Softkey engine for keypad / D-pad Android apps. Add a feature-phone style softkey bar — LEFT and RIGHT labels at the bottom of the screen, driven by the phone's physical keys — to any Android app with a few lines of integration code.

Repository / artifact name: `yapchik`.

```
┌──────────────────────────────┐
│                              │
│        app content           │
│                              │
├──────────────────────────────┤
│ Menu                    Back │   <- the softkey bar
└──────────────────────────────┘
   LSK                      RSK
```

Intended for kosher phones, keypad phones, and any device where the D-pad is the primary input. On hybrid devices (touch + keypad) the labels are also tappable.

## Capabilities

- **No key-handling boilerplate.** The library wraps the window callback; host apps never override `dispatchKeyEvent`.
- **App-wide defaults + per-screen configs.** Define default softkeys once for the whole app (e.g. LEFT = Options). Individual screens override per slot, suppress per slot, or opt out entirely.
- **Conditional keys and dynamic labels.** A key can hide itself based on state (`visibleIf { zoom < MAX }`) — while hidden, its label disappears and its physical key passes through. Labels can be computed per refresh (`"Zoom (3x)"`).
- **Named key-sets per screen.** Define several sets ("map" / "list" / "edit") once, switch between them with one call.
- **Focus overlays.** Softkeys can change while a given view has focus — the classic pattern where the keys follow the D-pad between fields.
- **On / Off / Automatic global mode**, persisted. AUTO detects keypad devices (12-key keyboard or no touchscreen; TVs excluded), so one APK behaves correctly on touch and keypad phones.
- **State reporting.** `Yapchik.isActive`, `Softkeys.isShownIn(activity)`, and change listeners, so the app can adjust its own UI.
- **Per-screen mode override** — force softkeys ON or OFF for one screen regardless of the global setting.
- **Key profiles.** Listens for every known softkey code by default (`SOFT_LEFT`/`MENU`/`F1`/`MULTIFUNC_LEFT` and `SOFT_RIGHT`/`F2`/`MULTIFUNC_RIGHT`, vendor names resolved per device), so most phones need no setup. Built-in presets, per-device and extra-keycode declarations in XML, a chooser dialog, and a press-your-keys calibration flow — all persisted.
- **Settings integration in one line** (`YapchikSettingsDialog.show(activity)`) or via a single property (`Yapchik.mode`) in your own settings screen.
- **Navigation-bar policy.** Conditional by default on every theme family: navbar hidden while the softkey bar shows, restored when softkeys are off. Three-way theme detection (Material3 / AppCompat / framework), with a per-device-tunable nav guard on framework themes whose vendor strips survive hide requests.
- **Zero dependencies.** Framework only — no androidx, no appcompat. Works with plain `Activity`, `AppCompatActivity`, anything. `minSdk 21`. Kotlin and Java hosts.

## Installation

**JitPack (recommended).** In `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
dependencies {
    implementation 'com.github.theonionsarewatching:yapchik:1.0.2'
}
```

If JitPack resolves the repo as multi-module, the coordinate is `com.github.theonionsarewatching.yapchik:yapchik:1.0.2`. Releases are built from git tags.

**Local module.** Copy the `yapchik/` module into your project, `include ':yapchik'` in settings.gradle, `implementation project(':yapchik')`.

**Vendored sources.** The engine is ~15 Kotlin files in one package with zero dependencies; copying `yapchik/src/main/java/com/theonionsarewatching/yapchik/` into an app also works.

## Minimal integration

**1. Install once**, in the Application class:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Yapchik.install(this)
    }
}
```

```xml
<application android:name=".App" ...>
```

**2. Declare softkeys on a screen** — either statically:

```kotlin
class MainActivity : Activity(), SoftkeyProvider {
    override fun onCreateSoftkeys(softkeys: SoftkeyConfig) {
        softkeys.left("Menu") { openMenu() }
        softkeys.right("Back") { finish() }
    }
}
```

or dynamically, from anywhere, at any time:

```kotlin
Softkeys.of(this).set {
    left("Save") { save() }
    right("Back") { finish() }
}
```

**3. Expose the setting** — one line behind a menu item is sufficient:

```kotlin
YapchikSettingsDialog.show(this)
```

On a keypad phone (or with mode = ON) the bar appears, the physical keys work, content is padded so nothing is covered, and the user's choices persist.

## Configuration model

Softkeys are resolved **per slot** through three layers, highest priority first:

| Layer | Defined by | Scope |
|---|---|---|
| 1. Focus overlay | `whenFocused(viewId) { }` | while that view (or a descendant) has focus |
| 2. Screen config | `SoftkeyProvider`, `set { }`, or the active named set | one Activity |
| 3. App defaults | `Yapchik.defaults { activity, keys -> }` | every Activity |

For each slot: a layer that **binds** it wins; a layer that **`remove()`s** it blocks the layers below (key passes through); a layer that doesn't mention it falls through to the next. A screen config can cut off layer 3 entirely with `noDefaults()`.

After resolution, two filters apply: `setVisible(slot, false)` and the binding's `visibleIf` condition. A slot that ends up hidden or unbound never consumes its physical key.

Label resolution: `setLabel(slot, ...)` override, else the binding's dynamic label lambda, else its static text.

### App-wide defaults

Register once, right after `install`:

```kotlin
Yapchik.defaults { activity, keys ->
    keys.left("Options") { YapchikSettingsDialog.show(activity) }
    keys.right("Back") { activity.finish() }
}
```

Every screen now has these without writing any code. A screen that binds LEFT replaces the default LEFT only; a screen that calls `remove(SoftkeySlot.RIGHT)` suppresses the default RIGHT only; a screen that calls `noDefaults()` starts blank. `Yapchik.defaults(null)` unregisters. Register defaults in `Application.onCreate` — screens that never touch the softkey API only pick up defaults registered before they are created.

**Softkeys on some screens only.** To keep the bar off everywhere by default and opt in per screen:

```kotlin
Yapchik.defaultScreenMode = SoftkeyMode.OFF     // no bar anywhere…
```

```kotlin
// …until a screen asks for it:
Softkeys.of(this).screenMode = SoftkeyMode.ON   // or `screenMode = SoftkeyMode.ON`
                                                // inside the provider / set { } block
```

Setting `screenMode = SoftkeyMode.OFF` inside a `Yapchik.defaults { }` block does the same thing. Mode resolution, highest first: the screen's own `screenMode` → a defaults-block `screenMode` → `Yapchik.defaultScreenMode` → the global `Yapchik.mode`.

### Conditional keys and dynamic labels

The map-view case — zoom keys that disappear at their limits and show the current level:

```kotlin
Softkeys.of(this).set {
    left(
        label = { "Zoom − (${zoom}x)" },          // re-read on every refresh
        visibleIf = { zoom > MIN_ZOOM }            // hidden + key passes through when false
    ) { zoomOut() }
    right(
        label = { "Zoom + (${zoom}x)" },
        visibleIf = { zoom < MAX_ZOOM }
    ) { zoomIn() }
}

private fun zoomOut() {
    zoom--
    redrawMap()
    Softkeys.of(this).invalidate()   // re-evaluates visibleIf + labels
}
```

`invalidate()` is the softkey equivalent of `invalidateOptionsMenu()`: call it whenever state read by your conditions or labels changes. It is cheap.

Imperative alternatives when a condition lambda is overkill:

```kotlin
Softkeys.of(this).setVisible(SoftkeySlot.LEFT, false)  // hide; key passes through
Softkeys.of(this).setLabel(SoftkeySlot.LEFT, "Saved")  // relabel without rebinding
Softkeys.of(this).setLabel(SoftkeySlot.LEFT, null)     // revert to the binding's label
```

### Named key-sets (per-screen modes)

For screens with distinct modes, define each set once and switch:

```kotlin
val keys = Softkeys.of(this)

keys.define("map") {
    left("Zoom −", visibleIf = { zoom > 1 }) { zoomOut() }
    right("Zoom +", visibleIf = { zoom < 7 }) { zoomIn() }
}
keys.define("list") {
    left("Filter") { openFilter() }
    right("Map") { keys.activate("map") }
}

keys.activate("map")          // switch modes with one call
keys.activeSetName            // "map"
keys.activate(null)           // back to the plain set { } config
```

`update { }` mutates whichever set is currently shown; `set { }` replaces the screen's config and deactivates any named set.

### Focus overlays

Change the softkeys while a particular view has focus (matches the view or any descendant, so container IDs work):

```kotlin
Softkeys.of(this).whenFocused(R.id.search_box) {
    left("Clear") { searchBox.text.clear() }
    right("Search") { runSearch() }
}
// Softkeys.of(this).clearWhenFocused(R.id.search_box)
```

Slots bound in the overlay override the screen config while focused; `remove()`d slots are blocked; everything else falls through. The overlay is applied and removed automatically as focus moves.

### Per-screen mode override

```kotlin
Softkeys.of(this).screenMode = SoftkeyMode.OFF   // never on this screen (e.g. video playback)
Softkeys.of(this).screenMode = SoftkeyMode.ON    // always on this screen
Softkeys.of(this).screenMode = null              // inherit the global mode
```

Also settable declaratively inside a provider / `set { }` / `define { }` block via `screenMode = ...`.

### Updating and relabeling

```kotlin
Softkeys.of(this).update {                 // mutate without clearing the rest
    left("Save*") { saveAgain() }
}
Softkeys.of(this).clear()                  // remove everything, incl. defaults
```

### The D-pad is never touched

Softkeys are strictly the LEFT and RIGHT keys of the active profile. The D-pad — including center/OK — plus ENTER, numbers, and every other normal key always keep their native behavior; OK always clicks the focused view. Unbound and hidden slots never consume their keys.

## Global mode and settings

The system is governed by one persisted property:

```kotlin
Yapchik.mode = SoftkeyMode.ON      // or OFF, or AUTO
```

Setting it persists, notifies listeners, and refreshes every live screen. `AUTO` (default) resolves per device — keypad phones get softkeys, touch phones don't. The detection heuristic is replaceable via `Yapchik.autoDetector` (e.g. to check a system property on a specific ROM).

**Built-in settings UI** (framework `AlertDialog`, includes the key-layout chooser):

```kotlin
YapchikSettingsDialog.show(this)                  // fire-and-forget
YapchikSettingsDialog.show(this) { refreshUi() }  // with change callback
```

**Custom settings screen** — it is one property. The test app's `SetupActivity` shows a plain `RadioGroup` version. With androidx Preference:

```xml
<ListPreference
    android:key="softkey_mode"
    android:title="Softkeys"
    android:entries="@array/softkey_mode_entries"     <!-- On / Off / Automatic -->
    android:entryValues="@array/softkey_mode_values"  <!-- ON / OFF / AUTO -->
    android:defaultValue="AUTO" />
```

```kotlin
findPreference<ListPreference>("softkey_mode")?.setOnPreferenceChangeListener { _, new ->
    Yapchik.mode = SoftkeyMode.fromString(new as String)
    true
}
```

The library keeps its own persistence; sync your preference UI *from* `Yapchik.mode` on startup.

## State reporting

```kotlin
Yapchik.isActive                            // resolved global state
Yapchik.mode                                // raw ON/OFF/AUTO setting
Softkeys.isShownIn(activity)                // bar visible on this screen?
Softkeys.of(activity).isActiveOnThisScreen  // per-screen resolved state
Yapchik.barHeightPx(context)                // for manual layout math
```

React to changes (user flips the setting mid-session):

```kotlin
val listener = Yapchik.StateListener { active -> relayout(active) }
Yapchik.addStateListener(listener)
// Yapchik.removeStateListener(listener) in onDestroy
```

Layout is handled automatically: while the bar is visible, the screen's content view gets bottom padding equal to the bar height; the padding is removed when the bar hides. To manage this manually, set `Yapchik.autoInsetContent = false` and use `barHeightPx` + the listener.

## Key profiles

Keypad devices differ in what their softkeys send. By default the library **listens for every known softkey code at once**, so most phones work with no setup at all:

| Profile | LEFT | RIGHT | Notes |
|---|---|---|---|
| `KeyProfile.UNIVERSAL` (default) | `SOFT_LEFT` (1), `MENU` (82), `F1` (131), `MULTIFUNC_LEFT`* | `SOFT_RIGHT` (2), `F2` (132), `MULTIFUNC_RIGHT`* | all known softkey codes at once |
| `KeyProfile.STANDARD` | `SOFT_LEFT` (1) | `SOFT_RIGHT` (2) | proper feature-phone keymaps |
| `KeyProfile.MENU_BACK` | `MENU` (82) | `BACK` (4) | many MTK keypad devices |
| `KeyProfile.FUNCTION` | `F1` (131) | `F2` (132) | some odd keymaps |

\* Vendor keycode names that aren't in the public SDK (some keypad ROMs report their softkeys as `KEYCODE_MULTIFUNC_LEFT` / `KEYCODE_MULTIFUNC_RIGHT`, kernel scancodes 357 / 444). Their numbers vary per ROM, so the library resolves them **by name against the device's own keycode table** at runtime — the same table `adb shell input keyevent NAME` uses. A name the ROM doesn't define is dropped, so it costs nothing on phones that lack it. Add your own with `KeyProfile.resolveNames("VENDOR_KEY")`.

**Keycodes vs scancodes:** `getevent` prints kernel *scancodes* (357/444 above); the library works in Android *keycodes*, which the device's `.kl` layout maps scancodes to. If you only know the scancode, run calibration — it captures the keycode the app actually receives.

**What `UNIVERSAL` deliberately leaves out:**

- **BACK.** It has a system meaning, so listening for it everywhere would swallow the back key on every screen with a RIGHT action. Devices whose right softkey *is* BACK use the `MENU_BACK` preset or a per-device XML entry.
- **Reserved keys** (D-pad, numbers, letters, volume…) — never touched by any profile.
- **Ambiguous codes** — anything that means LEFT on one phone and RIGHT on another can't be mapped blind; those belong in a per-device entry or calibration.

### Declaring keys in XML

```xml
<!-- res/xml/yapchik_devices.xml -->
<devices>
    <!-- Extra vendor codes appended to UNIVERSAL. Names (KEYCODE_ prefix
         optional) or raw numbers. Reserved keys and BACK are ignored here. -->
    <softkeys left="SOFT_LEFT,MENU,F1,139" right="SOFT_RIGHT,F2,140" />

    <!-- Per-device overrides, matched against Build.MODEL (case-insensitive).
         left/right replace the mapping on that device (BACK allowed);
         navGuardDp = "none" | integer dp for framework themes. -->
    <device model="SL006D" left="SOFT_LEFT" right="SOFT_RIGHT" navGuardDp="16" />
    <device model="XP3800" left="139" right="140" />
    <device model="SOME-CLEAN-PHONE" navGuardDp="none" />
</devices>
```

```kotlin
Yapchik.loadDeviceProfiles(this, R.xml.yapchik_devices)   // once, after install
```

**Resolution order** (highest first): the user's own choice (chooser or calibration, persisted) → a matching `<device>` entry → `UNIVERSAL` + the `<softkeys>` list.

```kotlin
Yapchik.keyProfile = KeyProfile.MENU_BACK              // programmatic, persisted
Yapchik.clearKeyProfileChoice()                        // back to device XML / automatic
Yapchik.hasUserKeyProfile                              // has the user chosen explicitly?
SoftkeyProfileChooser.show(this)                       // user-facing chooser dialog
SoftkeyProfileChooser.startCalibration(this)           // press-your-keys detection
Yapchik.keyProfile = KeyProfile("acme", "Acme K1",     // fleet-specific custom profile
    setOf(139), setOf(140))                            // left keycodes, right keycodes
```

Calibration asks the user to press LEFT, then RIGHT, captures the two keycodes, and persists them as a custom profile. BACK cancels calibration; on devices whose right softkey *is* BACK, use the `MENU_BACK` preset instead.

**Reserved keys.** Calibration never captures normal keys — D-pad, numbers, `*`/`#`, letters, typing keys, volume, call/power/camera, media. Pressing one shows a hint and the key keeps its normal function. The filter is a *blocklist* of known-normal keys, not an allowlist, because many keypad phones emit odd vendor keycodes for their softkeys — those are accepted. The same rule is enforced at runtime and when parsing XML: reserved keys are never consumed as softkeys even if a hand-built profile or XML entry contains them, so a bad profile can't break D-pad navigation, OK-clicks, or number input. `ReservedKeys.isReserved(keyCode)` exposes the check.

Consumption rule: a physical key is consumed only when softkeys are active on that screen **and** its slot resolves to a visible action **and** the key isn't reserved. With `MENU_BACK`, BACK behaves normally on any screen without a visible RIGHT action.

## Styling

```kotlin
Yapchik.style.apply {
    backgroundColor = 0xFF0D2137.toInt()
    textColor = 0xFFEEEEEE.toInt()
    pressedTextColor = 0xFFFFC107.toInt()
    heightDp = 46
    textSizeSp = 16f
    bold = true
}
Yapchik.refreshAll()   // if bars are already on screen
```

## Java usage

Callbacks are `Runnable`; entry points are `@JvmStatic`; condition/label lambdas target Kotlin function interfaces, which Java lambdas satisfy:

```java
Softkeys.of(this).set(config -> {
    config.put(SoftkeySlot.LEFT, "Menu", null, null, this::openMenu);
    config.put(SoftkeySlot.RIGHT, "Back", null, () -> zoom < MAX, this::finish);
    return kotlin.Unit.INSTANCE;
});
boolean on = Yapchik.isActive();
```

## Test app: PareveYapchik

`sample/` builds **PareveYapchik** in three side-by-side flavors — one per theme family, all running the library's default AUTO policy so they exercise the runtime theme detection:

| Flavor | App | Activity + theme | Detected as |
|---|---|---|---|
| `m3` | PareveYapchik M3 (`basically.kugel.m3`) | AppCompatActivity + Material3 | MATERIAL3 |
| `mat` | PareveYapchik M (`basically.kugel.m`) | AppCompatActivity + Theme.AppCompat | APPCOMPAT |
| `dd` | PareveYapchik DD (`basically.kugel.dd`) | plain Activity + Theme.DeviceDefault | FRAMEWORK (nav guard active while hiding) |

All three behave conditionally: navbar hidden while the softkey bar shows, restored when softkeys are off. The setup screen's nav-guard adjuster (−/+/Auto, persisted) appears only where it applies — the screen checks `Yapchik.themeKind(this)` and shows the row on FRAMEWORK themes (the DD flavor) only. `res/xml/yapchik_devices.xml` shows the key-declaration and per-device profile format. Note `AppCompatActivity` refuses to run framework themes, which is why DD uses a plain `Activity`. It is also a test app

```bash
./gradlew :sample:installM3Debug    # PareveYapchik M3
./gradlew :sample:installMatDebug   # PareveYapchik M
./gradlew :sample:installDdDebug    # PareveYapchik DD
```

## Testing without a keypad phone

Force the bar on (`Yapchik.mode = SoftkeyMode.ON` or via the settings dialog) and drive it from adb:

```bash
adb shell input keyevent 1     # KEYCODE_SOFT_LEFT
adb shell input keyevent 2     # KEYCODE_SOFT_RIGHT
adb shell input keyevent 82    # KEYCODE_MENU      (MENU_BACK profile)
adb shell input keyevent 4     # KEYCODE_BACK      (MENU_BACK profile)
adb shell input keyevent --longpress 1
```

## Troubleshooting

**Theme families and the navigation bar — how the library decides.** The library distinguishes three theme families per Activity (`Yapchik.themeKind`): MATERIAL3 (Material3 themes, detected via `colorPrimaryContainer`), APPCOMPAT (other AppCompat-family themes, via app-namespace `colorPrimary`), and FRAMEWORK (`Theme.DeviceDefault`, `Theme.Material`, …). This matters because `Theme.DeviceDefault` is the one theme device makers may overlay — the same app can behave differently per device on it, including vendor bottom strips that keep drawing after a navbar-hide request (the request still drops the navbar inset, expanding the window under the strip). MATERIAL3/APPCOMPAT themes ship inside the APK and are immune. Also note `AppCompatActivity` requires an AppCompat-descendant theme — pairing it with a framework theme crashes at launch.

`Yapchik.navigationBarPolicy` (default `AUTO`) is conditional on every family: navbar hidden while a softkey bar is visible, restored when it goes away, re-asserted on every start/resume/refresh. On FRAMEWORK themes, while hiding is in effect the bar additionally grows by the **nav guard** so its labels stay above any surviving vendor strip. Guard sizing: user-set `Yapchik.navGuardDp` (persisted; 0 disables) → per-device value from `Yapchik.loadDeviceProfiles(context, R.xml.yourfile)` matched by `Build.MODEL` → automatic (probable navbar height). Explicit `HIDE_WITH_BAR` / `HIDE_ALWAYS` / `LEAVE_ALONE` policies and `hideSystemNavigation` / `restoreSystemNavigation` remain available.

**Bar never shows on the test device.** Mode is `AUTO` on a touch device — expected. Set mode to ON from settings or `Yapchik.mode = SoftkeyMode.ON`.

**Keys do nothing on a real keypad phone.** The device routes softkeys differently. Open the chooser (`SoftkeyProfileChooser.show(this)`) and run calibration. If even calibration sees nothing, the device consumes its softkeys before apps get them — the tappable labels on the bar remain usable.

**Calibration refuses to accept a key.** That key is reserved (D-pad, number, letter, volume, call…) and keeps its normal function by design. Softkeys must use non-reserved codes; if the device's softkeys genuinely emit reserved codes (e.g. right softkey = BACK), use the `MENU_BACK` preset.

**A key stopped hiding/showing when expected.** `visibleIf` conditions are only re-evaluated on `invalidate()`/refresh. Call `invalidate()` after the state changes.

**BACK stopped working.** The `MENU_BACK` profile is active and the screen shows a RIGHT action — that action now owns BACK on that screen (typically it *is* "Back"). Screens without a visible RIGHT action are unaffected.

**Defaults show on a screen that shouldn't have them.** Call `noDefaults()` in that screen's config, or `remove(slot)` for a single slot.

**Softkeys inactive while a Dialog is open.** Dialogs own their window; key events go to the dialog, which is what D-pad users expect. The bar stays visible underneath.

**`requestWindowFeature()` conflict?** None — the library does not touch the window decor before `setContentView`.

**AppCompat / Fragments / multiple activities?** All fine. Fragments call `Softkeys.of(requireActivity()).set { }` when they take over the screen (or use `whenFocused` on their root view ID).

**Jetpack Compose?** Key interception works (window level) and the bar overlays fine, but auto-insetting pads the ComposeView; prefer `autoInsetContent = false` and handle spacing in composables via `barHeightPx` + the state listener. Designed primarily for View-based apps.

**Something else wraps `Window.callback`.** Wrappers compose — whoever wraps last sees events first. Install early and don't replace the callback wholesale afterwards.

## API reference

| Call | Purpose |
|---|---|
| `Yapchik.install(app)` | one-time setup in `Application.onCreate()` |
| `Yapchik.defaults { activity, keys -> }` | app-wide default softkeys (null to unregister) |
| `Yapchik.defaultScreenMode` | default per-screen mode (OFF = softkeys only where opted in) |
| `Yapchik.mode` | get/set ON / OFF / AUTO (persisted, live-refreshes) |
| `Yapchik.isActive` | resolved global state |
| `Yapchik.addStateListener / removeStateListener` | react to on/off changes |
| `Yapchik.keyProfile` | physical-key mapping — user choice > device XML > UNIVERSAL |
| `Yapchik.clearKeyProfileChoice()` / `hasUserKeyProfile` | forget / query the user's explicit choice |
| `Yapchik.loadDeviceProfiles(ctx, xml)` | extra softkey codes + per-device overrides + nav-guard |
| `KeyProfile.resolveNames("NAME", …)` | vendor keycode names → codes, resolved on this device |
| `Yapchik.style` / `refreshAll()` / `barHeightPx(ctx)` | styling + layout helpers |
| `Yapchik.autoInsetContent` / `autoDetector` | inset behavior, AUTO heuristic |
| `Yapchik.navigationBarPolicy` | AUTO (default, conditional everywhere) / HIDE_WITH_BAR / HIDE_ALWAYS / LEAVE_ALONE |
| `Yapchik.themeKind(activity)` | MATERIAL3 / APPCOMPAT / FRAMEWORK detection |
| `Yapchik.navGuardDp` / `loadDeviceProfiles(ctx, xml)` | FRAMEWORK-theme nav guard: user override / per-device XML |
| `Yapchik.hideSystemNavigation / restoreSystemNavigation` | manual navbar control for bar-less screens |
| `Softkeys.of(activity)` | this screen's controller |
| `Softkeys.isShownIn(activity)` | bar visible here? |
| `controller.set { } / update { } / clear()` | define / mutate / remove |
| `controller.define(name) { } / activate(name) / activeSetName` | named key-sets |
| `controller.whenFocused(id) { } / clearWhenFocused(id)` | focus overlays |
| `controller.invalidate()` | re-evaluate `visibleIf` + dynamic labels |
| `controller.setLabel(slot, text)` / `setVisible(slot, bool)` | imperative overrides |
| `controller.screenMode` | per-screen ON/OFF/inherit |
| `controller.isActiveOnThisScreen / isBarShown` | per-screen state |
| `config.left/right(label, onLongPress, visibleIf) { }` | bind keys (static or `{ }` dynamic label) |
| `config.remove(slot)` / `noDefaults()` | block a slot / cut off defaults |
| `config.screenMode` | declarative per-screen mode |
| `SoftkeyProvider.onCreateSoftkeys()` | static per-Activity declaration |
| `SoftkeyProfileChooser.show / startCalibration` | key-layout chooser / detection |
| `ReservedKeys.isReserved(keyCode)` | is this a normal key softkeys must never take? |
| `YapchikSettingsDialog.show(activity)` | drop-in settings dialog |

## Integration checklist

- [ ] `Yapchik.install(this)` in `Application.onCreate()` (+ `android:name` in manifest)
- [ ] Optional: `Yapchik.defaults { }` for app-wide keys
- [ ] Softkeys per screen (`SoftkeyProvider` or `Softkeys.of(this).set { }`)
- [ ] `invalidate()` calls wherever `visibleIf`/dynamic-label state changes
- [ ] Settings reachable (`YapchikSettingsDialog.show(this)` behind a menu item is enough; includes the key-layout chooser)
- [ ] Test with `Yapchik.mode = ON` + adb keyevents before shipping

## License

**GNU Lesser General Public License v3.0 or later** 
```
Copyright (C) 2026 theOnionsAreWatching

This library is free software: you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version.
```
