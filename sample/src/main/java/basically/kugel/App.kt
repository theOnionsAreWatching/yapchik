package basically.kugel

import android.app.Application
import android.widget.Toast
import com.theonionsarewatching.yapchik.Yapchik
import com.theonionsarewatching.yapchik.YapchikSettingsDialog

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Yapchik.install(this)

        // All three flavors use the library default (AUTO): conditional on
        // every theme family — navbar hidden while the softkey bar shows,
        // restored when softkeys are off. The library detects the theme
        // family (Material3 / AppCompat / framework) per Activity itself;
        // on framework themes it adds the nav guard while hiding.
        Yapchik.navigationBarPolicy = Yapchik.NavBarPolicy.AUTO

        // Key mappings + per-device settings (extra softkey codes, per-model
        // overrides, nav-guard sizes). Without this the library still listens
        // for every known softkey code by default.
        Yapchik.loadDeviceProfiles(this, R.xml.yapchik_devices)

        // Compact bar for small keypad screens. All of these are library
        // options (Yapchik.style) any app can set.
        Yapchik.style.heightDp = 34
        Yapchik.style.textSizeSp = 12f
        Yapchik.style.horizontalPaddingDp = 6

        // App-wide default: LEFT = Options on every screen that doesn't
        // override it. FocusActivity leaves LEFT unbound at base level, so
        // this default is visible there (until the text box takes focus).
        Yapchik.defaults { activity, keys ->
            keys.left("Options") {
                Toast.makeText(activity, "Options (default key)", Toast.LENGTH_SHORT).show()
                YapchikSettingsDialog.show(activity)
            }
        }
    }
}
