package odon.utils.oskeyboarddisabler

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class KeyboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val service = KeyboardAccessibilityService.instance
        if (service == null) {
            openAccessibilitySettings()
            return
        }
        service.toggle()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = KeyboardAccessibilityService.instance != null
        val hidden = running && KeyboardAccessibilityService.isHidden(this)

        tile.label = getString(R.string.tile_label)
        tile.state = if (hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (hidden) R.drawable.ic_no_keyboard else R.drawable.ic_keyboard
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    !running -> R.string.tile_service_off
                    hidden -> R.string.tile_hidden
                    else -> R.string.tile_allowed
                }
            )
        }
        tile.updateTile()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
