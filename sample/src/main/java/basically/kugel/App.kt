package basically.kugel

import android.app.Application
import android.widget.Toast
import com.theonionsarewatching.yapchik.Yapchik
import com.theonionsarewatching.yapchik.YapchikSettingsDialog

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Yapchik.install(this)

        // Flavor matrix: "with_bar" hides the navbar only while a softkey
        // bar is visible (touch users with softkeys off keep their navbar);
        // "always" hides it on every screen.
        Yapchik.navigationBarPolicy = when (BuildConfig.NAV_MODE) {
            "with_bar" -> Yapchik.NavBarPolicy.HIDE_WITH_BAR
            "always" -> Yapchik.NavBarPolicy.HIDE_ALWAYS
            else -> Yapchik.NavBarPolicy.AUTO
        }

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
