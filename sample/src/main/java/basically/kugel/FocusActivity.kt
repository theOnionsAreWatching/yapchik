package basically.kugel

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.theonionsarewatching.yapchik.Softkeys

/**
 * Focus demo: the softkeys change when the text box has focus.
 *
 * Base config (focus anywhere else): RIGHT = Back; LEFT is left unbound at
 * base level, so the app-wide default ("Options", from App.kt) shows —
 * demonstrating per-slot fallback to defaults.
 *
 * While the text box (or a descendant) has focus, the [Softkeys.whenFocused]
 * overlay takes over: LEFT = Clear, RIGHT = Done. Labels and toasts change
 * with focus so the switch is verifiable.
 */
class FocusActivity : BaseTestActivity() {

    private lateinit var textBox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus)

        textBox = findViewById(R.id.text_box)
        val otherButton = findViewById<Button>(R.id.btn_other)
        otherButton.setOnClickListener { toast("Plain button clicked") }

        val keys = Softkeys.of(this)

        // Base: only RIGHT — LEFT falls through to the app default "Options".
        keys.set {
            right("Back") {
                toast("Back")
                finish()
            }
        }

        // Overlay while the text box has focus:
        keys.whenFocused(R.id.text_box) {
            left("Clear") {
                toast("Clear")
                textBox.text.clear()
            }
            right("Done") {
                toast("Done")
                otherButton.requestFocus() // leaving the box restores base keys
            }
        }

        textBox.setOnFocusChangeListener { _, _ -> updateState() }
        otherButton.setOnFocusChangeListener { _, _ -> updateState() }
        updateState()
    }

    private fun updateState() {
        val inBox = textBox.hasFocus()
        findViewById<TextView>(R.id.focus_state).text =
            if (inBox) "Focus: IN the text box \u2192 softkeys are Clear / Done"
            else "Focus: outside the text box \u2192 softkeys are Options / Back"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
