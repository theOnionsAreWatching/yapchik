package com.theonionsarewatching.yapchik

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.util.Collections
import java.util.WeakHashMap

/**
 * Yapchik — a drop-in softkey engine for keypad / D-pad-first Android apps.
 *
 * Entry point and global configuration. Typical setup:
 *
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Yapchik.install(this)
 *     }
 * }
 * ```
 *
 * Then per screen, either implement [SoftkeyProvider] on the Activity, or call
 * [Softkeys.of] `(activity).set { ... }` at any time.
 */
object Yapchik {

    /** Fired whenever the resolved on/off state may have changed (mode change). */
    fun interface StateListener {
        fun onSoftkeyStateChanged(active: Boolean)
    }

    /** Builds the app-wide default softkeys for a given Activity. See [defaults]. */
    fun interface SoftkeyDefaults {
        fun onCreateDefaultSoftkeys(activity: Activity, softkeys: SoftkeyConfig)
    }

    @Volatile
    private var app: Application? = null

    private val controllers =
        Collections.synchronizedMap(WeakHashMap<Activity, SoftkeyController>())
    private val listeners = mutableListOf<StateListener>()

    /**
     * Visual styling shared by every softkey bar (colors, height, text size…).
     * Mutate freely; call [refreshAll] afterwards if bars are already on screen.
     */
    val style = YapchikStyle()

    /**
     * When true (the default), Yapchik automatically adds bottom padding to the
     * screen's content view while the bar is visible, so the bar never covers
     * your UI. Set to false if you prefer to manage layout yourself using
     * [barHeightPx] and the state listeners.
     */
    var autoInsetContent = true

    /** The three theme families the library distinguishes. See [themeKind]. */
    enum class ThemeKind {
        /** Google Material library, Material3 themes (ship inside the APK). */
        MATERIAL3,

        /** AppCompat-family themes that are not Material3 (ship inside the APK). */
        APPCOMPAT,

        /**
         * Framework themes — Theme.DeviceDefault, Theme.Material, etc.
         * Theme.DeviceDefault is the one theme device makers may overlay, so
         * its behavior (including vendor bottom strips that ignore navbar
         * hide requests) varies per device by design.
         */
        FRAMEWORK
    }

    /**
     * Which theme family an Activity is running. Detection is by attribute
     * resolution against the live theme: Material3 defines
     * `colorPrimaryContainer`, AppCompat defines an app-namespace
     * `colorPrimary`; anything else is a framework theme.
     */
    fun themeKind(activity: Activity): ThemeKind = when {
        resolvesAppAttr(activity, "colorPrimaryContainer") -> ThemeKind.MATERIAL3
        resolvesAppAttr(activity, "colorPrimary") -> ThemeKind.APPCOMPAT
        else -> ThemeKind.FRAMEWORK
    }

    private fun resolvesAppAttr(activity: Activity, name: String): Boolean = try {
        val attr = activity.resources.getIdentifier(name, "attr", activity.packageName)
        attr != 0 && activity.theme.resolveAttribute(attr, android.util.TypedValue(), true)
    } catch (_: Exception) {
        false
    }

    /** How the system navigation bar is managed. See [navigationBarPolicy]. */
    enum class NavBarPolicy {
        /**
         * Default: conditional on every theme family — the navigation bar is
         * hidden while a softkey bar is visible and restored when it goes
         * away (behaves as [HIDE_WITH_BAR]). On FRAMEWORK themes
         * (Theme.DeviceDefault etc.) the softkey bar additionally grows by
         * the nav-guard height while hiding is in effect, because vendor
         * builds of DeviceDefault may keep drawing their bottom strip even
         * after a successful-looking hide request; the guard keeps the
         * labels above it. See [navGuardDp] and [loadDeviceProfiles].
         */
        AUTO,

        /**
         * The navigation bar is hidden while a softkey bar is visible and
         * restored when it goes away — touch users who turn softkeys OFF get
         * their navigation bar back.
         */
        HIDE_WITH_BAR,

        /** The navigation bar is hidden on every Activity, always. */
        HIDE_ALWAYS,

        /** The library never touches system UI. */
        LEAVE_ALONE
    }

    /**
     * Navigation-bar policy, applied automatically (re-asserted on every
     * activity start/resume and bar refresh so it survives dialogs and focus
     * changes). Change it any time; takes effect on the next refresh.
     */
    var navigationBarPolicy = NavBarPolicy.AUTO

    /** Resolve [NavBarPolicy.AUTO] for a concrete Activity. */
    fun resolvedNavBarPolicy(activity: Activity): NavBarPolicy {
        val p = navigationBarPolicy
        return if (p == NavBarPolicy.AUTO) NavBarPolicy.HIDE_WITH_BAR else p
    }

    // ------------------------------------------------------------ nav guard

    @Volatile
    private var deviceProfileGuardDp: Int? = null

    private var _navGuardDp: Int? = null

    /**
     * Extra height (dp) added to the bottom of the softkey bar on FRAMEWORK
     * themes while a navbar hide is in effect — keeps the labels above
     * vendor bottom strips that survive hide requests.
     *
     * `null` (default) = automatic: a per-device value from
     * [loadDeviceProfiles] if one matched, else the device's probable
     * navbar height. Set an explicit dp value (0 disables the guard) to
     * override; persisted across runs. Not used on MATERIAL3/APPCOMPAT
     * themes, which are immune to vendor theme overlays.
     */
    var navGuardDp: Int?
        get() = _navGuardDp
        set(value) {
            _navGuardDp = value
            YapchikPrefs.saveNavGuard(value)
            refreshAll()
        }

    /**
     * Optionally load per-device settings from an XML resource:
     *
     * ```xml
     * <devices>
     *     <!-- model = android.os.Build.MODEL, case-insensitive.
     *          navGuardDp = "none" | integer dp -->
     *     <device model="SL006D" navGuardDp="16" />
     *     <device model="SOME-CLEAN-DEVICE" navGuardDp="none" />
     * </devices>
     * ```
     *
     * A matching entry sets the automatic nav-guard for this device; an
     * explicit [navGuardDp] set by the user still wins over it.
     */
    @JvmStatic
    fun loadDeviceProfiles(context: Context, xmlResId: Int) {
        try {
            val parser = context.resources.getXml(xmlResId)
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "device"
                ) {
                    val model = parser.getAttributeValue(null, "model")
                    if (model != null && model.equals(Build.MODEL, ignoreCase = true)) {
                        val guard = parser.getAttributeValue(null, "navGuardDp")
                        deviceProfileGuardDp =
                            if (guard != null && guard.equals("none", ignoreCase = true)) 0
                            else guard?.toIntOrNull()
                    }
                }
                parser.next()
            }
        } catch (_: Exception) {
            // malformed profile files must never crash the host app
        }
    }

    /** Resolved guard height in px for this Activity (FRAMEWORK themes only). */
    internal fun navGuardPx(activity: Activity): Int {
        val explicitDp = _navGuardDp ?: deviceProfileGuardDp
        if (explicitDp != null) {
            return (explicitDp * activity.resources.displayMetrics.density).toInt()
        }
        return probableNavBarHeight(activity)
    }

    /**
     * Best guess at the height of a navigation bar / vendor bottom strip:
     * live insets when available; after a hide request the system reports
     * zero even when a vendor strip keeps drawing, so this falls back to the
     * device's declared navigation_bar_height.
     */
    private fun probableNavBarHeight(activity: Activity): Int {
        var h = 0
        val insets = activity.window?.peekDecorView()?.rootWindowInsets
        if (insets != null) {
            h = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        if (h == 0) {
            val res = activity.resources
            val showId = res.getIdentifier("config_showNavigationBar", "bool", "android")
            if (showId != 0 && res.getBoolean(showId)) {
                val heightId = res.getIdentifier("navigation_bar_height", "dimen", "android")
                if (heightId != 0) h = res.getDimensionPixelSize(heightId)
            }
        }
        return h
    }

    /**
     * Device detection used when the mode is [SoftkeyMode.AUTO].
     * Replace with your own lambda if you need custom logic
     * (e.g. checking a system property on a specific kosher-phone ROM).
     */
    var autoDetector: (Context) -> Boolean = AutoDetect::deviceLooksLikeKeypad

    /** True once [install] has been called. */
    val isInstalled: Boolean
        get() = app != null

    /**
     * Install Yapchik. Call exactly once, from [Application.onCreate].
     * Loads the persisted mode + key profile and hooks activity lifecycles.
     */
    @JvmStatic
    fun install(application: Application) {
        if (app != null) return
        app = application
        YapchikPrefs.init(application)
        _mode = YapchikPrefs.loadMode()
        _keyProfile = YapchikPrefs.loadProfile()
        _navGuardDp = YapchikPrefs.loadNavGuard()
        application.registerActivityLifecycleCallbacks(lifecycleHook)
    }

    // -------------------------------------------------------------- defaults

    @Volatile
    private var defaultsProvider: SoftkeyDefaults? = null

    /**
     * App-wide default softkeys, applied to every Activity. Register once,
     * right after [install]:
     *
     * ```kotlin
     * Yapchik.defaults { activity, keys ->
     *     keys.left("Options") { YapchikSettingsDialog.show(activity) }
     *     keys.right("Back") { activity.finish() }
     * }
     * ```
     *
     * Per-screen configs override defaults *per slot*: a slot the screen binds
     * wins, a slot the screen `remove()`s is suppressed, and untouched slots
     * show the default. A screen opts out entirely with
     * [SoftkeyConfig.noDefaults]. Pass `null` to unregister.
     *
     * Register before activities are created (Application.onCreate) —
     * activities that never touch the softkey API only pick up defaults added
     * before their creation.
     */
    @JvmStatic
    fun defaults(provider: SoftkeyDefaults?) {
        defaultsProvider = provider
        refreshAll()
    }

    /** True when app-wide defaults are registered. */
    val hasDefaults: Boolean
        get() = defaultsProvider != null

    internal fun buildDefaults(activity: Activity): SoftkeyConfig? {
        val provider = defaultsProvider ?: return null
        val config = SoftkeyConfig()
        provider.onCreateDefaultSoftkeys(activity, config)
        return config
    }

    // ------------------------------------------------------------------ mode

    private var _mode: SoftkeyMode = SoftkeyMode.AUTO

    /**
     * Global mode: [SoftkeyMode.ON], [SoftkeyMode.OFF] or [SoftkeyMode.AUTO].
     * Setting this persists the value, notifies [StateListener]s and refreshes
     * every live screen. This is the property to wire into your app's settings.
     */
    var mode: SoftkeyMode
        get() = _mode
        set(value) {
            if (_mode == value) return
            _mode = value
            YapchikPrefs.saveMode(value)
            notifyStateChanged()
            refreshAll()
        }

    // --------------------------------------------------------------- profile

    private var _keyProfile: KeyProfile = KeyProfile.STANDARD

    /**
     * The active key profile: which physical keycodes map to LEFT and
     * RIGHT. Persisted automatically. See [KeyProfile.BUILT_IN] and
     * [SoftkeyProfileChooser].
     */
    var keyProfile: KeyProfile
        get() = _keyProfile
        set(value) {
            _keyProfile = value
            YapchikPrefs.saveProfile(value)
        }

    // ----------------------------------------------------------------- state

    /**
     * The resolved global state — what ON/OFF/AUTO currently means on this
     * device. This is the flag host apps should check when they need to adjust
     * their own UI ("are softkeys on right now?").
     */
    @JvmStatic
    val isActive: Boolean
        get() = resolve(_mode)

    /** Resolve a specific mode value against this device. */
    fun resolve(mode: SoftkeyMode): Boolean = when (mode) {
        SoftkeyMode.ON -> true
        SoftkeyMode.OFF -> false
        SoftkeyMode.AUTO -> app?.let { autoDetector(it) } ?: false
    }

    /** Register for on/off changes (e.g. to re-layout your UI). */
    fun addStateListener(listener: StateListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeStateListener(listener: StateListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    internal fun notifyStateChanged() {
        val active = isActive
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it.onSoftkeyStateChanged(active) }
    }

    /** Re-evaluates and redraws the softkey bar on every live screen. */
    @JvmStatic
    fun refreshAll() {
        val copy = synchronized(controllers) { controllers.values.toList() }
        copy.forEach { it.refresh() }
    }

    /**
     * Height of the softkey bar in pixels — useful when [autoInsetContent] is
     * disabled and you handle layout adjustments yourself.
     */
    fun barHeightPx(context: Context): Int = style.heightPx(context)

    /**
     * Hide the system navigation bar for this Activity so the softkey bar
     * sits flush with the physical bottom edge of the screen.
     *
     * Android shows the navigation bar to every app by default; apps opt out.
     * On keypad devices the navigation bar is useless (nothing to tap), so
     * keypad apps hide it.
     *
     * Uses [WindowInsetsController] on Android 11+ and legacy sticky-immersive
     * flags (self-re-applying) below that. The window is deliberately NOT
     * extended under the navigation bar: when the bar hides, the content
     * resizes to fill the screen; if a ROM refuses to hide its navigation
     * bar, the softkey bar simply sits above it — never behind it.
     *
     * Note: [navigationBarPolicy] normally drives this automatically — the
     * manual call is only needed for special cases (e.g. LEAVE_ALONE policy
     * with individual screens that still want it, or a bar-less screen that
     * should stay navbar-free under HIDE_WITH_BAR).
     */
    @JvmStatic
    fun hideSystemNavigation(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.peekDecorView() ?: return

        val apply = {
            // Modern path (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.navigationBars())
                }
            }
            // Legacy path — applied on ALL API levels as well: it is routed
            // through the compat layer on modern Android, and vendor keypad
            // ROMs frequently only honor this older signal.
            @Suppress("DEPRECATION")
            run {
                val flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                decor.systemUiVisibility = decor.systemUiVisibility or flags
                // Also persist the flags in the window params so they survive
                // system-initiated clears better than the view flag alone.
                val lp = window.attributes
                lp.systemUiVisibility = lp.systemUiVisibility or flags
                window.attributes = lp
                decor.setOnSystemUiVisibilityChangeListener { visibility ->
                    if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
                        decor.systemUiVisibility = decor.systemUiVisibility or flags
                    }
                }
            }
        }

        apply()
        // On a fresh launch the decor is not attached to the window manager
        // yet during onCreate/onStart/onResume — re-apply once it is.
        decor.post { apply() }
    }

    /** Undo [hideSystemNavigation] for this Activity. */
    @JvmStatic
    fun restoreSystemNavigation(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.peekDecorView() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.navigationBars())
        }
        @Suppress("DEPRECATION")
        run {
            val flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            decor.setOnSystemUiVisibilityChangeListener(null)
            decor.systemUiVisibility = decor.systemUiVisibility and flags.inv()
            val lp = window.attributes
            lp.systemUiVisibility = lp.systemUiVisibility and flags.inv()
            window.attributes = lp
        }
    }

    // ------------------------------------------------------------ controllers

    /**
     * Returns (creating and attaching if needed) the [SoftkeyController] for an
     * Activity. Usually accessed through [Softkeys.of].
     */
    @JvmStatic
    fun controllerFor(activity: Activity): SoftkeyController {
        check(app != null) {
            "Yapchik.install(application) must be called before using softkeys. " +
                "Do it in your Application.onCreate()."
        }
        synchronized(controllers) {
            controllers[activity]?.let { return it }
            val controller = SoftkeyController(activity)
            controllers[activity] = controller
            controller.attach()
            return controller
        }
    }

    internal fun controllerOrNull(activity: Activity): SoftkeyController? =
        controllers[activity]

    // -------------------------------------------------------------- lifecycle

    private val lifecycleHook = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            // Providers always get a controller; with app-wide defaults
            // registered, every activity does (so codeless screens still show
            // the default softkeys).
            if (activity is SoftkeyProvider || defaultsProvider != null) {
                controllerFor(activity).rebuildFromProvider()
            }
        }

        override fun onActivityStarted(activity: Activity) {
            if (resolvedNavBarPolicy(activity) == NavBarPolicy.HIDE_ALWAYS) {
                hideSystemNavigation(activity)
            }
            controllers[activity]?.refresh()
        }

        override fun onActivityResumed(activity: Activity) {
            if (resolvedNavBarPolicy(activity) == NavBarPolicy.HIDE_ALWAYS) {
                hideSystemNavigation(activity)
            }
            controllers[activity]?.refresh()
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            controllers.remove(activity)?.detach()
        }
    }
}
