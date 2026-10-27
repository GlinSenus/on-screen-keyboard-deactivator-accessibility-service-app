package odon.utils.oskeyboarddisabler

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var settingsButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        status = findViewById<TextView>(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        settingsButton = findViewById<MaterialButton>(R.id.settingsButton)

        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val running = KeyboardAccessibilityService.instance != null
        status.setText (if (running) R.string.service_active else R.string.service_inactive)
        val colorRes = if (running) R.color.status_active else R.color.status_inactive
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }
}