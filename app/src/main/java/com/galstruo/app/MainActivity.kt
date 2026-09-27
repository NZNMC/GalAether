package com.galstruo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            // 只收集一份设置:第一份真实设置读到前显示空白,读到后直接渲染真实值。
            // 不能用 collectAsStateWithLifecycle(initialValue = UiSettings()):
            // 默认设置里 onboardingDone=false,会让新手引导在每次启动时闪一下。
            var uiSettings by remember { mutableStateOf<UiSettings?>(null) }
            LaunchedEffect(Unit) {
                settings.uiSettings.collect { uiSettings = it }
            }
            val s = uiSettings
            if (s == null) {
                // 与启动画面同色系的空白页,避免黑屏/闪白
                val blank = if (isSystemInDarkTheme()) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
                Box(Modifier.fillMaxSize().background(blank))
                return@setContent
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (s.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            GalStoreTheme(
                darkTheme = darkTheme,
                dynamicColor = s.dynamicColor,
                seedHue = s.seedHue,
            ) {
                GalStoreApp(settings = settings, uiSettings = s)
            }
        }
    }
}
