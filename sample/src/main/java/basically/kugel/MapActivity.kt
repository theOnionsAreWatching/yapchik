package basically.kugel

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.theonionsarewatching.yapchik.Softkeys

/**
 * Map screen: LEFT = zoom out, RIGHT = zoom in, range 1x-7x.
 *
 * Compact dynamic labels ("Zoom+ 3x"). Each key carries a `visibleIf`
 * condition: fully zoomed in, "Zoom+" disappears from the bar and its
 * physical key passes through; same for "Zoom-" at minimum. Every press
 * toasts "Zoom +1 (4x)", "Zoom +2 (5x)"... so the physical key, the label,
 * and the action can be checked against each other.
 */
class MapActivity : BaseTestActivity() {

    private var zoom = START_ZOOM
    private var zoomInPresses = 0
    private var zoomOutPresses = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        findViewById<Button>(R.id.btn_back_menu).setOnClickListener { finish() }

        val keys = Softkeys.of(this)

        // Two named key-sets, defined once, switched with activate():
        keys.define("map") {
            noDefaults() // this set manages its keys itself
            // Compact dynamic labels; each key hides at its limit (visibleIf)
            // and its physical key passes through while hidden.
            left(
                label = { "Zoom\u2212 ${zoom}x" },
                visibleIf = { zoom > MIN_ZOOM }
            ) { zoomOut() }
            right(
                label = { "Zoom+ ${zoom}x" },
                visibleIf = { zoom < MAX_ZOOM }
            ) { zoomIn() }
        }
        keys.define("list") {
            noDefaults()
            left("Filter") { toast("Filter") }
            right("Map") {
                toast("Map")
                keys.activate("map")
                render()
            }
        }
        keys.activate("map")

        findViewById<Button>(R.id.btn_mode).setOnClickListener {
            keys.activate(if (keys.activeSetName == "map") "list" else "map")
            render()
        }

        render()
    }

    private fun zoomIn() {
        if (zoom >= MAX_ZOOM) return // key is hidden at max anyway
        zoom++
        zoomInPresses++
        toast("Zoom +$zoomInPresses (${zoom}x)")
        render()
        Softkeys.of(this).invalidate() // re-evaluates labels + visibleIf
    }

    private fun zoomOut() {
        if (zoom <= MIN_ZOOM) return // key is hidden at min anyway
        zoom--
        zoomOutPresses++
        toast("Zoom \u2212$zoomOutPresses (${zoom}x)")
        render()
        Softkeys.of(this).invalidate() // re-evaluates labels + visibleIf
    }

    private fun render() {
        findViewById<TextView>(R.id.map_status).text = buildString {
            append("Mode: ${Softkeys.of(this@MapActivity).activeSetName}   ")
            append("Zoom: ${zoom}x  (range ${MIN_ZOOM}x\u2013${MAX_ZOOM}x)")
            if (zoom == MIN_ZOOM) append("\nAt minimum — Zoom\u2212 is hidden, its key passes through")
            if (zoom == MAX_ZOOM) append("\nAt maximum — Zoom+ is hidden, its key passes through")
        }
        // A fake "map": tile grid that gets coarser as you zoom in.
        val cells = (MAX_ZOOM - zoom) + 2
        val row = "\u25A6 ".repeat(cells).trim()
        findViewById<TextView>(R.id.map_canvas).text =
            Array(cells) { row }.joinToString("\n")
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MIN_ZOOM = 1
        const val MAX_ZOOM = 7
        const val START_ZOOM = 3
    }
}
