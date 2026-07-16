package basically.kugel

import androidx.appcompat.app.AppCompatActivity

/**
 * AppCompatActivity + Theme.AppCompat: "AppCompat but not Material3".
 * (AppCompatActivity cannot run under Theme.Material / Theme.DeviceDefault —
 * appcompat rejects non-AppCompat themes at launch.)
 */
open class BaseTestActivity : AppCompatActivity()
