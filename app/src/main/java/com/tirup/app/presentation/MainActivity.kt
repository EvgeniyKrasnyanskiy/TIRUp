package com.tirup.app.presentation

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tirup.app.TirupApplication
import com.tirup.app.presentation.focus.FocusScreen
import com.tirup.app.presentation.focus.FocusViewModel
import com.tirup.app.presentation.navigation.Screen
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
import com.tirup.app.presentation.theme.TextSecondaryDark
import com.tirup.app.presentation.trends.TrendsScreen
import com.tirup.app.presentation.trends.TrendsViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TirupApplication
        val glucoseRepo = app.glucoseRepository
        val settingsRepo = app.settingsRepository
        val importer = app.streamingImporter

        val focusViewModel = FocusViewModel(glucoseRepo, settingsRepo)
        val trendsViewModel = TrendsViewModel(glucoseRepo, settingsRepo)
        val reportsViewModel = ReportsViewModel(this, glucoseRepo, settingsRepo, importer)
        val settingsViewModel = SettingsViewModel(settingsRepo, glucoseRepo, importer)

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()

            // Update app locale on runtime setting change
            val currentLocale = rememberUpdatedLocale(settingsState.userSettings.language)

            CompositionLocalProvider {
                TIRUpTheme {
                    MainAppContent(
                        focusViewModel = focusViewModel,
                        trendsViewModel = trendsViewModel,
                        reportsViewModel = reportsViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    @Composable
    private fun rememberUpdatedLocale(languageCode: String): Locale {
        val targetLocale = if (languageCode.equals("EN", ignoreCase = true)) Locale.ENGLISH else Locale("ru")
        val configuration = LocalConfiguration.current
        val context = LocalContext.current

        LaunchedEffect(languageCode) {
            Locale.setDefault(targetLocale)
            val config = Configuration(configuration)
            config.setLocale(targetLocale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }

        return targetLocale
    }
}

@Composable
fun MainAppContent(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(
        Screen.Focus,
        Screen.Trends,
        Screen.Reports,
        Screen.Settings
    )

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                            label = { Text(text = stringResource(screen.titleResId)) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryEmerald,
                                selectedTextColor = PrimaryEmerald,
                                indicatorColor = PrimaryEmerald.copy(alpha = 0.15f),
                                unselectedIconColor = TextMutedDark,
                                unselectedTextColor = TextMutedDark
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
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
            NavHost(
                navController = navController,
                startDestination = Screen.Focus.route
            ) {
                composable(Screen.Focus.route) {
                    FocusScreen(
                        viewModel = focusViewModel,
                        onNavigateToReports = {
                            navController.navigate(Screen.Reports.route)
                        }
                    )
                }
                composable(Screen.Trends.route) {
                    TrendsScreen(viewModel = trendsViewModel)
                }
                composable(Screen.Reports.route) {
                    ReportsScreen(viewModel = reportsViewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
