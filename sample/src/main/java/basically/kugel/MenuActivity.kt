package basically.kugel

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.theonionsarewatching.yapchik.SoftkeyConfig
import com.theonionsarewatching.yapchik.SoftkeyProvider
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik
import com.theonionsarewatching.yapchik.YapchikSettingsDialog

/**
 * D-pad friendly menu of the demo screens. Declares its softkeys statically
 * via [SoftkeyProvider]. D-pad OK always clicks the focused option — the
 * library never touches the D-pad.
 */
class MenuActivity : BaseTestActivity(), SoftkeyProvider {

    private val stateListener = Yapchik.StateListener { updateState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        findViewById<Button>(R.id.btn_map).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        findViewById<Button>(R.id.btn_focus).setOnClickListener {
            startActivity(Intent(this, FocusActivity::class.java))
        }
        findViewById<Button>(R.id.btn_setup).setOnClickListener { finish() }

        Yapchik.addStateListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Yapchik.removeStateListener(stateListener)
    }

    override fun onCreateSoftkeys(softkeys: SoftkeyConfig) {
        softkeys.left("Options") {
            toastKey("Options")
            YapchikSettingsDialog.show(this) { updateState() }
        }
        softkeys.right(
            "Exit",
            onLongPress = { toastKey("Exit (long press)") }
        ) {
            toastKey("Exit")
            finishAffinity()
        }
    }

    private fun updateState() {
        findViewById<TextView>(R.id.menu_state).text =
            "Softkeys: ${if (Yapchik.isActive) "ON" else "OFF"} " +
            "(mode: ${Yapchik.mode})\n" +
            "Bar shown here: ${Softkeys.isShownIn(this)}\n" +
            "Layout: ${Yapchik.keyProfile.describe()}"
    }

    private fun toastKey(label: String) =
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
}
