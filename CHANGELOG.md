# Changelog

## 1.0.2 — 2026-07-17

- **License changed to LGPL-3.0-or-later** (was PolyForm Noncommercial 1.0.0).
  Any app, under any license, may link and ship the library; changes to the
  library itself stay copyleft and users must be able to relink. FSF-free and
  OSI-approved, so it's fine for F-Droid. LICENSE holds the LGPL text and
  LICENSE.GPL-3.0 the GPL text it incorporates
- New default profile KeyProfile.UNIVERSAL — listens for every known softkey
  code at once (SOFT_LEFT/MENU/F1 and SOFT_RIGHT/F2, plus vendor names such
  as MULTIFUNC_LEFT/MULTIFUNC_RIGHT resolved against the device's own keycode
  table via KeyProfile.resolveNames), so most keypad phones work with no setup. BACK is excluded (system meaning; use MENU_BACK or a
  per-device entry), as are reserved keys and any code whose slot is
  ambiguous across devices
- loadDeviceProfiles XML gains <softkeys left=… right=…> for extra vendor
  keycodes and left/right attributes on <device> for per-model mappings;
  keycodes accept names (KEYCODE_ prefix optional) or numbers. Resolution:
  user choice > matching <device> entry > UNIVERSAL + <softkeys> list
- Yapchik.clearKeyProfileChoice() / hasUserKeyProfile; the persisted profile
  now records only explicit user choices
- Yapchik.defaultScreenMode (and screenMode inside a defaults block): set OFF
  for apps that want softkeys on some screens only — screens opt in with
  screenMode = ON
- Test app: the nav-guard adjuster now appears only on FRAMEWORK themes (DD
  flavor) via Yapchik.themeKind; setup screen also reports the detected theme
  family and whether the layout was chosen or automatic

## 1.0.1 — 2026-07-16

- Three-way theme detection (Yapchik.themeKind): MATERIAL3 /
  APPCOMPAT / FRAMEWORK — Theme.DeviceDefault is OEM-overlayable and can
  behave differently per device by design; library themes ship in the APK
- AUTO navbar policy is now conditional on every theme family: navbar
  hidden while the softkey bar is visible, restored when softkeys are off
- FRAMEWORK-theme nav guard: while hiding is in effect the bar grows by a
  guard height so labels stay above vendor strips that survive hide
  requests; sizing order = user-set Yapchik.navGuardDp (persisted, 0
  disables) > per-device XML profile (Yapchik.loadDeviceProfiles, matched
  by Build.MODEL) > automatic probable navbar height
- Test app reduced to three flavors (M3 / M / DD), one per theme family,
  all on the default AUTO policy; DD setup screen gains a nav-guard
  adjuster; example device-profiles XML included

## 1.0.0 — 2026-07-16

Initial release. Licensed under PolyForm Noncommercial 1.0.0.

- Softkey bar with LEFT / RIGHT slots, tappable labels, press flash;
  the D-pad (incl. OK), numbers, and other normal keys are never touched
- Window-callback key interception (no dispatchKeyEvent code in host apps)
- Layered per-slot resolution: focus overlay > screen config > app defaults
- App-wide default softkeys (`Yapchik.defaults`) with per-slot override,
  per-slot suppression (`remove`), and full opt-out (`noDefaults`)
- Conditional keys (`visibleIf`) — hidden keys pass through to the app
- Dynamic labels (label lambdas) + `invalidate()` re-evaluation
- Named key-sets per screen (`define` / `activate`) for mode switching
- Focus overlays (`whenFocused`) — softkeys follow D-pad focus
- Imperative overrides: `setLabel`, `setVisible`
- Long-press actions per slot
- ON / OFF / AUTO global mode, persisted, with keypad-device auto-detection
- State reporting: `Yapchik.isActive`, `Softkeys.isShownIn`, state listeners
- Per-screen mode override (`screenMode`)
- Key profiles: STANDARD, MENU_BACK, FUNCTION + custom; chooser dialog +
  press-your-keys calibration
- Reserved-key protection: calibration never captures normal keys (D-pad,
  numbers, letters, volume, call keys…) and the runtime never consumes them
  for LEFT/RIGHT; blocklist approach so vendor softkey codes stay accepted
- Softkey labels render in sans-serif-medium (synthetic bold clips glyphs on
  some keypad-phone font stacks)
- Drop-in settings dialog (`YapchikSettingsDialog`)
- Automatic content insetting under the bar; styling hooks
- Test app "PareveYapchik" (basically.kugel): key setup screen (softkeys
  forced off there via screenMode), D-pad menu with focus outlines, compact
  layouts for small keypad displays, map screen (zoom keys hide at their
  limits via visibleIf, compact dynamic labels, named sets), focus screen
  (whenFocused overlay + app-default fallback); every softkey press toasts
  its label for key/label/action verification
- Navigation-bar policy (Yapchik.navigationBarPolicy): AUTO (default,
  theme-keyed — navbar removed completely on Material3 themes, hands-off on
  framework themes so the bar sits above any navbar or vendor strip),
  HIDE_WITH_BAR (hidden while a softkey bar is visible, restored when off),
  HIDE_ALWAYS, LEAVE_ALONE; hiding fires WindowInsetsController (11+) plus
  legacy immersive and window-attribute flags, re-asserted on
  start/resume/refresh; documented ROM lesson: a hide request drops the
  navbar inset even when a vendor strip keeps drawing, so not requesting
  the hide is the only safe configuration on such ROMs
- M3 flavor runs AppCompatActivity + Material3 — the exact runtime profile
  of the production keypad apps — with the navbar removed completely by the
  AUTO policy
- Test app in four flavors — {conditional, always-hide} x {UI profile}:
  CM3 (AppCompatActivity+Material3, HIDE_WITH_BAR), CM
  (AppCompatActivity+Theme.AppCompat, HIDE_WITH_BAR), AM (same theme,
  HIDE_ALWAYS), AD (plain Activity+Theme.DeviceDefault, HIDE_ALWAYS);
  CI builds and publishes all four APKs
- Test app moved off Theme.DeviceDefault to Theme.Material.NoActionBar:
  vendor keypad ROMs key their framework-level bottom softkey strip to the
  DeviceDefault theme family (documented in README troubleshooting)
- GitHub Actions CI: builds the AAR + PareveYapchik debug APK on every
  push; tag pushes publish a GitHub Release with both attached
