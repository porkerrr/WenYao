package com.peterpork.liuyao

import android.os.Bundle
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.peterpork.liuyao.ui.WenYaoApp
import com.peterpork.liuyao.ui.WenYaoTheme
import com.peterpork.liuyao.ui.HomeEntranceSession

class MainActivity : ComponentActivity() {
    internal lateinit var homeEntrance: HomeEntranceSession
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app always uses light paper, including when the system is in night mode.
        // Draw paper behind the bars on every supported API; Compose owns content insets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        homeEntrance = HomeEntranceSession(
            restored = savedInstanceState != null,
            normalLaunch = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER),
            visible = Build.VERSION.SDK_INT < 31,
        )
        if (Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { overlay ->
            // Never hold the platform splash for branding or resource loading.
            overlay.remove()
            homeEntrance.contentVisible = true
        }
        val app = application as WenYaoApplication
        setContent {
            WenYaoTheme {
                WenYaoApp(app.database.dao(), app.analysisRepository, app.credentialStore, app.settingsRepository, homeEntrance,
                    presentation = app.castPresentation(intent))
            }
        }
    }

    override fun onPause() {
        homeEntrance.finish()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        homeEntrance.finish()
        super.onSaveInstanceState(outState)
    }
}
