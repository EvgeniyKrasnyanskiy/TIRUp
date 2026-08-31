package com.tirup.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import com.tirup.app.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Focus : Screen("focus", R.string.nav_focus, Icons.Default.TrackChanges)
    object Trends : Screen("trends", R.string.nav_trends, Icons.Default.ShowChart)
    object Reports : Screen("reports", R.string.nav_reports, Icons.Default.Description)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}
