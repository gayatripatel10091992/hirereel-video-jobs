package com.gayatripatel.hirereel.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gayatripatel.hirereel.ui.applications.ApplicationsRoute
import com.gayatripatel.hirereel.ui.feed.FeedRoute
import com.gayatripatel.hirereel.ui.interview.InterviewRoute
import com.gayatripatel.hirereel.ui.jobs.JobDetailRoute
import com.gayatripatel.hirereel.ui.jobs.JobsRoute
import com.gayatripatel.hirereel.ui.login.LoginRoute

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    FEED("feed", "For you", Icons.Filled.PlayCircle),
    JOBS("jobs", "Search", Icons.Filled.Search),
    APPLICATIONS("applications", "Applied", Icons.AutoMirrored.Filled.Assignment),
}

@Composable
fun TabShell(selected: Tab?, onSelect: (Tab) -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun HireReelNav(signedIn: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tab = Tab.entries.firstOrNull { it.route == entry?.destination?.route }

    fun openTab(t: Tab) = nav.navigate(t.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    TabShell(tab, ::openTab) { modifier ->
        NavHost(nav, startDestination = if (signedIn) Tab.FEED.route else "login", modifier = modifier) {
            composable("login") {
                LoginRoute(onSignedIn = { nav.navigate(Tab.FEED.route) { popUpTo("login") { inclusive = true } } })
            }
            composable(Tab.FEED.route) { FeedRoute(onApply = { nav.navigate("job/$it") }) }
            composable(Tab.JOBS.route) { JobsRoute(onOpen = { nav.navigate("job/$it") }) }
            composable(Tab.APPLICATIONS.route) { ApplicationsRoute() }
            composable("job/{id}") { backStack ->
                JobDetailRoute(
                    jobId = backStack.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onStartInterview = { nav.navigate("interview/$it") },
                )
            }
            composable("interview/{id}") { backStack ->
                InterviewRoute(
                    jobId = backStack.arguments?.getString("id").orEmpty(),
                    onClose = { nav.popBackStack() },
                    onSubmitted = { openTab(Tab.APPLICATIONS) },
                )
            }
        }
    }
}
