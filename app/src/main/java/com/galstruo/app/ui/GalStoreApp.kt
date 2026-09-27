package com.galstruo.app.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.BounceIcon
import com.galstruo.app.ui.components.DownloadIcon
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
    Scaffold(
        bottomBar = {
            // 搜索页与详情页全屏展示,不显示底部导航
            if (currentRoute in bottomRoutes) {
                NavigationBar(
                    // 圆润悬浮式:与屏幕边缘留出间距,大圆角 + 阴影,像浮在内容上方
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp)
                        .shadow(18.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BounceIcon(
                                    selected = currentRoute == item.route,
                                    icon = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            // 页面切换动画:进入=淡入+上滑+轻微放大,返回=淡入,退出=淡出+下滑
            enterTransition = {
                fadeIn(tween(220)) +
                    slideInVertically(tween(300), initialOffsetY = { it / 12 }) +
                    scaleIn(tween(300), initialScale = 0.95f)
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutVertically(tween(260), targetOffsetY = { it / 12 })
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
                    onOpenThemeSettings = { navController.navigate("theme") },
                    onOpenHistory = { navController.navigate("history") },
                    onOpenKungalLogin = { navController.navigate("kungalLogin") },
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
                )
            }
        }
    }
}
