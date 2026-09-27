package com.galstruo.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.galstruo.app.data.FontSize
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.BounceIcon
import com.galstruo.app.ui.components.DownloadIcon
import com.galstruo.app.ui.components.LocalMotionEnabled
import com.galstruo.app.ui.components.LocalShowJapaneseNames
import com.galstruo.app.ui.components.LocalShowNsfw
import com.galstruo.app.ui.components.pressScale
import com.galstruo.app.ui.onboarding.OnboardingScreen
import com.galstruo.app.ui.screens.CategoryScreen
import com.galstruo.app.ui.screens.DownloadsScreen
import com.galstruo.app.ui.screens.FavoritesScreen
import com.galstruo.app.ui.screens.GameDetailScreen
import com.galstruo.app.ui.screens.HistoryScreen
import com.galstruo.app.ui.screens.HomeScreen
import com.galstruo.app.ui.screens.KungalLoginScreen
import com.galstruo.app.ui.screens.ProfileScreen
import com.galstruo.app.ui.screens.SearchScreen
import com.galstruo.app.ui.screens.ThemeSettingsScreen
import kotlinx.coroutines.launch

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem("home", "首页", Icons.Filled.Home),
    BottomItem("category", "分类", Icons.AutoMirrored.Filled.List),
    BottomItem("downloads", "下载", DownloadIcon),
    BottomItem("favorites", "收藏", Icons.Filled.Favorite),
    BottomItem("profile", "我的", Icons.Filled.Person),
)

private val bottomRoutes = bottomItems.map { it.route }.toSet()

private fun openGameRoute(game: GameItem): String =
    "game/${game.gameId}?orgName=${Uri.encode(game.orgName.orEmpty())}"

@Composable
fun GalStoreApp(settings: SettingsStore, uiSettings: UiSettings) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val motion = uiSettings.animationsEnabled
    // 字体大小:只放大文字,布局间距不变
    val fontScale = when (uiSettings.fontSize) {
        FontSize.SMALL -> 0.9f
        FontSize.LARGE -> 1.15f
        FontSize.NORMAL -> 1f
    }
    // 全局 UI 参数下发:动画总开关、字体缩放、日文原名显示、NSFW 开关
    CompositionLocalProvider(
        LocalMotionEnabled provides motion,
        LocalDensity provides LocalDensity.current.let { Density(it.density, fontScale = fontScale) },
        LocalShowJapaneseNames provides uiSettings.showJapaneseNames,
        LocalShowNsfw provides uiSettings.showNsfw,
    ) {
        // 首次启动:先展示新手引导,完成后才进入主界面
        if (!uiSettings.onboardingDone) {
            val scope = rememberCoroutineScope()
            OnboardingScreen(onDone = {
                scope.launch { settings.setOnboardingDone(true) }
            })
            return@CompositionLocalProvider
        }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    // 搜索页与详情页全屏展示,不显示底部导航
                    if (currentRoute in bottomRoutes) {
                        // 悬浮胶囊底栏:整个一条胶囊(圆角=高度一半),内容全部收在胶囊内不会溢出
                        Box(Modifier.navigationBarsPadding()) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 3.dp,
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
                            ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            bottomItems.forEach { item ->
                                val selected = currentRoute == item.route
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent,
                                            RoundedCornerShape(24.dp),
                                        )
                                        .pressScale()
                                        .clickable {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                ) {
                                    BounceIcon(
                                        selected = selected,
                                        icon = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            // 页面切换动画:进入=淡入+上滑+轻微放大,返回=淡入,退出=淡出+下滑;动画开关关闭时直接切换
            enterTransition = {
                if (motion) {
                    fadeIn(tween(220)) +
                        slideInVertically(tween(300), initialOffsetY = { it / 12 }) +
                        scaleIn(tween(300), initialScale = 0.95f)
                } else EnterTransition.None
            },
            exitTransition = { if (motion) fadeOut(tween(150)) else ExitTransition.None },
            popEnterTransition = { if (motion) fadeIn(tween(220)) else EnterTransition.None },
            popExitTransition = {
                if (motion) {
                    fadeOut(tween(180)) + slideOutVertically(tween(260), targetOffsetY = { it / 12 })
                } else ExitTransition.None
            },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenSearch = { navController.navigate("search") },
                    onOpenGame = { navController.navigate(openGameRoute(it)) },
                    uiSettings = uiSettings,
                )
            }
            composable("category") {
                CategoryScreen(
                    onOpenGame = { navController.navigate(openGameRoute(it)) },
                    uiSettings = uiSettings,
                )
            }
            composable("downloads") { DownloadsScreen() }
            composable("favorites") {
                FavoritesScreen(onOpenGame = { gid -> navController.navigate("game/$gid") })
            }
            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGame = { gid -> navController.navigate("game/$gid") },
                )
            }
            composable("profile") {
                ProfileScreen(
                    settings = settings,
                    onOpenThemeSettings = { navController.navigate("theme") },
                    onOpenHistory = { navController.navigate("history") },
                    onOpenKungalLogin = { navController.navigate("kungalLogin") },
                    uiSettings = uiSettings,
                )
            }
            composable("kungalLogin") {
                KungalLoginScreen(onBack = { navController.popBackStack() })
            }
            composable("theme") {
                ThemeSettingsScreen(
                    settings = settings,
                    uiSettings = uiSettings,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGame = { navController.navigate(openGameRoute(it)) },
                    uiSettings = uiSettings,
                )
            }
            composable(
                route = "game/{gid}?orgName={orgName}",
                arguments = listOf(
                    navArgument("gid") { type = NavType.LongType },
                    navArgument("orgName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                GameDetailScreen(
                    gid = entry.arguments?.getLong("gid") ?: 0L,
                    orgName = entry.arguments?.getString("orgName"),
                    onBack = { navController.popBackStack() },
                    onOpenGame = { navController.navigate(openGameRoute(it)) },
                )
            }
            }
        }
        }
    }
}
