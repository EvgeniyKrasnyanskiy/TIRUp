package com.tirup.app.presentation

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tirup.app.TirupApplication
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.presentation.components.HelpAndDisclaimerDialog
import com.tirup.app.presentation.focus.FocusScreen
import com.tirup.app.presentation.focus.FocusViewModel
import com.tirup.app.presentation.reports.ReportsScreen
import com.tirup.app.presentation.reports.ReportsViewModel
import com.tirup.app.presentation.settings.SettingsScreen
import com.tirup.app.presentation.settings.SettingsViewModel
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TIRUpTheme
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
            val themeMode = settingsState.userSettings.themeMode
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            ProvideLocalizedApp(languageCode = languageCode) {
                TIRUpTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigationRoot(
                            focusViewModel = focusViewModel,
                            trendsViewModel = trendsViewModel,
                            reportsViewModel = reportsViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ProvideLocalizedApp(
        languageCode: String,
        content: @Composable () -> Unit
    ) {
        val targetLocale = if (languageCode.equals("EN", ignoreCase = true)) Locale.ENGLISH else Locale("ru")
        val currentConfiguration = LocalConfiguration.current

        val localizedConfiguration = remember(languageCode, currentConfiguration) {
            Configuration(currentConfiguration).apply {
                setLocale(targetLocale)
                setLayoutDirection(targetLocale)
            }
        }

        LaunchedEffect(languageCode) {
            Locale.setDefault(targetLocale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(localizedConfiguration, resources.displayMetrics)
        }

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
        ) {
            androidx.compose.runtime.key(languageCode) {
                content()
            }
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
    val settingsState by settingsViewModel.uiState.collectAsState()

    if (!settingsState.userSettings.hasSeenOnboarding) {
        val isRu = settingsState.userSettings.language.equals("RU", ignoreCase = true)
        HelpAndDisclaimerDialog(
            isRu = isRu,
            onDismiss = {
                settingsViewModel.setHasSeenOnboarding(true)
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = "main_pager"
    ) {
        composable("main_pager") {
            MainPagerScaffold(
                focusViewModel = focusViewModel,
                trendsViewModel = trendsViewModel,
                reportsViewModel = reportsViewModel,
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScaffold(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    onOpenSettings: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val tabs = listOf(
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_focus),
            icon = Icons.Default.Adjust
        ),
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_trends),
            icon = Icons.AutoMirrored.Filled.TrendingUp
        ),
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_reports),
            icon = Icons.Default.Description
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.height(64.dp)
                ) {
                    tabs.forEachIndexed { index, item ->
                        val selected = pagerState.currentPage == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = null,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ActionBlue,
                                unselectedIconColor = MaterialTheme.colorScheme.outline,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
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

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)
