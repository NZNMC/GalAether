package com.galstruo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.ThemeMode
import com.galstruo.app.data.UiSettings
import com.galstruo.app.ui.GalStoreApp
import com.galstruo.app.ui.theme.GalStoreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        setContent {
            val uiSettings by settings.uiSettings.collectAsStateWithLifecycle(initialValue = UiSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            GalStoreTheme(
                darkTheme = darkTheme,
                dynamicColor = uiSettings.dynamicColor,
                seedHue = uiSettings.seedHue,
            ) {
                GalStoreApp(settings = settings, uiSettings = uiSettings)
            }
        }
    }
}
