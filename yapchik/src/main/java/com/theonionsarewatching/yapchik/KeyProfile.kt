package com.theonionsarewatching.yapchik

import android.view.KeyEvent

/**
 * Maps physical keycodes to the LEFT and RIGHT softkey slots.
 *
 * Different keypad phones route their softkeys differently:
 * proper KEYCODE_SOFT_LEFT/RIGHT, MENU/BACK, or even F1/F2. Pick a built-in
 * profile, or let the user calibrate a custom one with
 * [SoftkeyProfileChooser.startCalibration].
 *
 * The D-pad (including center/OK), ENTER, numbers, and all other normal keys
 * are never part of a profile — they keep their native behavior always.
 */
class KeyProfile(
    val id: String,
    val displayName: String,
    val leftKeys: Set<Int>,
    val rightKeys: Set<Int>
) {

    /** Which slot (if any) does this keycode belong to under this profile? */
    fun slotFor(keyCode: Int): SoftkeySlot? = when (keyCode) {
        in leftKeys -> SoftkeySlot.LEFT
        in rightKeys -> SoftkeySlot.RIGHT
        else -> null
    }

    /** Human-readable mapping summary, e.g. for a settings screen. */
    fun describe(): String {
        fun names(keys: Set<Int>) = keys.joinToString("/") {
            KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")
        }
        return "L:${names(leftKeys)}  R:${names(rightKeys)}"
    }

    companion object {
        const val CUSTOM_ID = "custom"

        /** KEYCODE_SOFT_LEFT / KEYCODE_SOFT_RIGHT. */
        @JvmField
        val STANDARD = KeyProfile(
            "standard", "Standard softkeys",
            setOf(KeyEvent.KEYCODE_SOFT_LEFT),
            setOf(KeyEvent.KEYCODE_SOFT_RIGHT)
        )

        /**
         * MENU as left, BACK as right — common on MTK keypad devices that never
         * emit real softkey codes. Note: BACK is only consumed on screens that
         * actually show a RIGHT action; otherwise it passes through normally.
         */
        @JvmField
        val MENU_BACK = KeyProfile(
            "menu_back", "Menu / Back keys",
            setOf(KeyEvent.KEYCODE_MENU),
            setOf(KeyEvent.KEYCODE_BACK)
        )

        /** F1 / F2 as the softkeys — seen on some feature-phone keymaps. */
        @JvmField
        val FUNCTION = KeyProfile(
            "function", "F1 / F2 keys",
            setOf(KeyEvent.KEYCODE_F1),
            setOf(KeyEvent.KEYCODE_F2)
        )

        @JvmField
        val BUILT_IN = listOf(STANDARD, MENU_BACK, FUNCTION)

        /**
         * Build a custom profile, e.g. from calibration. Persisted by id.
         * Note: reserved normal keys (see [ReservedKeys]) are never consumed
         * at runtime even if included here.
         */
        @JvmStatic
        fun custom(left: Int, right: Int) = KeyProfile(
            CUSTOM_ID, "Custom (detected)",
            setOf(left), setOf(right)
        )
    }
}
