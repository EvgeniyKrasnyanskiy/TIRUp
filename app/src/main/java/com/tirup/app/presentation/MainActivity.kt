package com.tirup.app.presentation

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tirup.app.TirupApplication
import com.tirup.app.presentation.focus.FocusScreen
import com.tirup.app.presentation.focus.FocusViewModel
import com.tirup.app.presentation.reports.ReportsScreen
import com.tirup.app.presentation.reports.ReportsViewModel
import com.tirup.app.presentation.settings.SettingsScreen
import com.tirup.app.presentation.settings.SettingsViewModel
import com.tirup.app.presentation.theme.DarkBg
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurface
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TIRUpTheme
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.trends.TrendsScreen
import com.tirup.app.presentation.trends.TrendsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TirupApplication
        val database = app.database
        val glucoseRepo = app.glucoseRepository
        val settingsRepo = app.settingsRepository
        val importer = app.streamingImporter

        val focusViewModel = FocusViewModel(glucoseRepo, settingsRepo)
        val trendsViewModel = TrendsViewModel(glucoseRepo, settingsRepo)
        val reportsViewModel = ReportsViewModel(this, glucoseRepo, settingsRepo, importer, database)
        val settingsViewModel = SettingsViewModel(settingsRepo, glucoseRepo)

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val languageCode = settingsState.userSettings.language

            // Dynamically update locale without breaking Activity Context for ActivityResultLauncher
            UpdateLocale(this, languageCode)

            TIRUpTheme {
                AppNavigationRoot(
                    focusViewModel = focusViewModel,
                    trendsViewModel = trendsViewModel,
                    reportsViewModel = reportsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    @Composable
    private fun UpdateLocale(context: Context, languageCode: String) {
        val configuration = LocalConfiguration.current
        LaunchedEffect(languageCode) {
            val targetLocale = if (languageCode.equals("EN", ignoreCase = true)) Locale.ENGLISH else Locale("ru")
            Locale.setDefault(targetLocale)
            val config = Configuration(configuration)
            config.setLocale(targetLocale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}

@Composable
fun AppNavigationRoot(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainSwipeablePagerContent(
                focusViewModel = focusViewModel,
                trendsViewModel = trendsViewModel,
                reportsViewModel = reportsViewModel,
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

data class BottomNavTab(
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainSwipeablePagerContent(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    onOpenSettings: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val tabs = listOf(
        BottomNavTab("Профиль", Icons.Default.Adjust),
        BottomNavTab("Тренды", Icons.AutoMirrored.Filled.TrendingUp),
        BottomNavTab("Отчёт", Icons.Default.Description)
    )

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp)),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(56.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = pagerState.currentPage == index
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = null,
                            alwaysShowLabel = false,
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryEmerald,
                                indicatorColor = PrimaryEmerald.copy(alpha = 0.15f),
                                unselectedIconColor = TextMutedDark
                            ),
                            onClick = {
                                if (pagerState.currentPage != index) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FocusScreen(viewModel = focusViewModel, onOpenSettings = onOpenSettings)
                    1 -> TrendsScreen(viewModel = trendsViewModel, onOpenSettings = onOpenSettings)
                    2 -> ReportsScreen(viewModel = reportsViewModel, onOpenSettings = onOpenSettings)
                }
            }
        }
    }
}
