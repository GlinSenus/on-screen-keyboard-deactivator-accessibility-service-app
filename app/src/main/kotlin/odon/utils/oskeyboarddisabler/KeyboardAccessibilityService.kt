package odon.utils.oskeyboarddisabler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.SoftKeyboardController
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent

class KeyboardAccessibilityService : AccessibilityService() {

    private var floatingButton: FloatingButton? = null

    private val showModeListener =
        SoftKeyboardController.OnShowModeChangedListener { _, mode ->
            val hidden = mode == SHOW_MODE_HIDDEN
            saveHidden(this, hidden)
            floatingButton?.setHidden(hidden)
            refreshTile(this)
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val hidden = isHidden(this)
        if (hidden) {
            softKeyboardController.showMode = SHOW_MODE_HIDDEN
        }
        softKeyboardController.addOnShowModeChangedListener(showModeListener)

        floatingButton = FloatingButton(this) { toggle() }.also { it.show(hidden) }

        refreshTile(this)
    }

    fun toggle(): Boolean {
        val controller = softKeyboardController
        val hide = controller.showMode != SHOW_MODE_HIDDEN
        val newMode = if (hide) SHOW_MODE_HIDDEN else SHOW_MODE_AUTO
        controller.showMode = newMode

        saveHidden(this, hide)
        floatingButton?.setHidden(hide)
        refreshTile(this)
        return hide
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingButton?.keepOnScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no need
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        softKeyboardController.removeOnShowModeChangedListener(showModeListener)
        floatingButton?.remove()
        floatingButton = null
        instance = null
        refreshTile(this)
        return super.onUnbind(intent)
    }

    companion object {
        private const val PREFS = "keyboard_toggle"
        private const val KEY_HIDDEN = "hidden"

        @Volatile
        var instance: KeyboardAccessibilityService? = null

        fun isHidden(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)

        fun saveHidden(context: Context, hidden: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_HIDDEN, hidden).apply()
        }

        fun refreshTile(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, KeyboardTileService::class.java)
            )
        }
    }
}
