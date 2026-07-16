package basically.kugel

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.SoftkeyProfileChooser
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik

/**
 * First screen: key insertion / setup. Pick the mode, choose or detect the
 * key layout, then continue to the demo menu.
 */
class SetupActivity : BaseTestActivity() {

    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val group = findViewById<RadioGroup>(R.id.mode_group)
        syncRadios(group)

        group.setOnCheckedChangeListener { _, checkedId ->
            if (syncing) return@setOnCheckedChangeListener
            Yapchik.mode = when (checkedId) {
                R.id.mode_on -> SoftkeyMode.ON
                R.id.mode_off -> SoftkeyMode.OFF
                else -> SoftkeyMode.AUTO
            }
            updateState()
        }

        findViewById<Button>(R.id.btn_choose).setOnClickListener { chooseLayout() }
        findViewById<Button>(R.id.btn_detect).setOnClickListener {
            SoftkeyProfileChooser.startCalibration(this) { updateState() }
        }
        findViewById<Button>(R.id.btn_continue).setOnClickListener { goToMenu() }

        // No softkey bar on the setup screen — it conflicts with the setup
        // inputs. Per-screen override: force OFF regardless of the global
        // mode (a library feature: Softkeys.of(activity).screenMode).
        Softkeys.of(this).screenMode = SoftkeyMode.OFF

        updateState()
    }

    override fun onResume() {
        super.onResume()
        syncRadios(findViewById(R.id.mode_group))
        updateState()
    }

    private fun chooseLayout() =
        SoftkeyProfileChooser.show(this) { updateState() }

    private fun goToMenu() =
        startActivity(Intent(this, MenuActivity::class.java))

    private fun syncRadios(group: RadioGroup) {
        syncing = true
        group.check(
            when (Yapchik.mode) {
                SoftkeyMode.ON -> R.id.mode_on
                SoftkeyMode.OFF -> R.id.mode_off
                SoftkeyMode.AUTO -> R.id.mode_auto
            }
        )
        syncing = false
    }

    private fun updateState() {
        findViewById<TextView>(R.id.setup_state).text =
            "Softkeys resolved: ${if (Yapchik.isActive) "ON" else "OFF"} " +
            "(mode: ${Yapchik.mode})\n" +
            "Layout: ${Yapchik.keyProfile.displayName}\n${Yapchik.keyProfile.describe()}"
    }

}
