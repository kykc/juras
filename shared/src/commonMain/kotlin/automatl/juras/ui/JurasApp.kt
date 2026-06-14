package automatl.juras.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import automatl.juras.domain.AppStateStore
import automatl.juras.domain.DarkModePreference
import automatl.juras.protocol.MachineCatalog
import automatl.juras.ui.theme.JurasTheme
import automatl.juras.ui.screens.BrewScreen
import automatl.juras.ui.screens.BrewingScreen
import automatl.juras.ui.screens.PairingScreen
import automatl.juras.ui.screens.PresetEditorScreen
import automatl.juras.ui.screens.SettingsScreen
import automatl.juras.ui.screens.StatusScreen
import juras.shared.generated.resources.Res
import juras.shared.generated.resources.ic_coffee
import org.jetbrains.compose.resources.painterResource

private data class TabItem(val route: Route, val label: String, val icon: @Composable () -> Unit)

private val TABS = listOf(
    TabItem(Route.Brew, "Brew") {
        Icon(painterResource(Res.drawable.ic_coffee), contentDescription = null)
    },
    TabItem(Route.Status, "Status") {
        Icon(Icons.Filled.Info, contentDescription = null)
    },
    TabItem(Route.Settings, "Settings") {
        Icon(Icons.Filled.Settings, contentDescription = null)
    },
)

@Composable
fun JurasApp(store: AppStateStore) {
    val appViewModel: AppViewModel = viewModel { AppViewModel(store) }
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val state = appState

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appState?.darkModePreference ?: DarkModePreference.SYSTEM) {
        DarkModePreference.SYSTEM -> systemDark
        DarkModePreference.DARK   -> true
        DarkModePreference.LIGHT  -> false
    }

    JurasTheme(darkTheme = darkTheme) {
        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@JurasTheme
        }

    val navController = rememberNavController()
    val startDestination: Route = remember {
        if (state.pairedDevice == null) Route.Pairing else Route.Brew
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TABS.any { currentDestination.isRoute(it.route) }

    val catalog = remember(state.pairedDevice?.model) {
        MachineCatalog.forModel(state.pairedDevice?.model ?: "EF1030")
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) JurasBottomBar(navController, currentDestination)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Route.Brew> {
                BrewScreen(
                    state = state,
                    catalog = catalog,
                    onBrew = { preset -> navController.navigate(Route.Brewing(preset.id)) },
                    onEdit = { preset -> navController.navigate(Route.PresetEditor(preset.id)) },
                    onAddPreset = { navController.navigate(Route.PresetEditor()) },
                    onQuickBrew = { navController.navigate(Route.QuickBrew) },
                    onReorder = { presets -> appViewModel.reorderPresets(presets) },
                )
            }
            composable<Route.Status> {
                StatusScreen(device = state.pairedDevice)
            }
            composable<Route.Settings> {
                SettingsScreen(
                    device = state.pairedDevice,
                    darkModePreference = state.darkModePreference,
                    onDarkModePreferenceChange = { appViewModel.setDarkModePreference(it) },
                    onEditConnection = { navController.navigate(Route.Pairing) },
                    onUnpair = {
                        appViewModel.unpair()
                        navController.navigate(Route.Pairing) { popUpTo(0) }
                    },
                    onRename = { name ->
                        state.pairedDevice?.let {
                            appViewModel.savePairedDevice(it.copy(machineName = name))
                        }
                    },
                    exportConfig = { appViewModel.exportConfig() },
                    parseConfig = { appViewModel.parseConfig(it) },
                    applyConfig = { appViewModel.applyConfig(it) },
                )
            }
            composable<Route.Pairing> {
                PairingScreen(
                    existing = state.pairedDevice,
                    onSaved = { device ->
                        appViewModel.pairDevice(device)
                        navController.navigate(Route.Brew) { popUpTo(0) }
                    },
                    parseConfig = { appViewModel.parseConfig(it) },
                    applyConfig = { appViewModel.applyConfig(it) },
                    onImported = { navController.navigate(Route.Brew) { popUpTo(0) } },
                )
            }
            composable<Route.PresetEditor> { backStack ->
                val presetId = backStack.toRoute<Route.PresetEditor>().presetId
                PresetEditorScreen(
                    preset = presetId?.let { id -> state.presets.firstOrNull { it.id == id } },
                    catalog = catalog,
                    onSave = { preset ->
                        appViewModel.upsertPreset(preset)
                        navController.popBackStack()
                    },
                    onDelete = { id ->
                        appViewModel.deletePreset(id)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<Route.QuickBrew> {
                PresetEditorScreen(
                    preset = null,
                    catalog = catalog,
                    onBrewNow = { preset ->
                        appViewModel.setPendingBrew(preset)
                        navController.navigate(Route.Brewing()) {
                            popUpTo(Route.QuickBrew) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<Route.Brewing> { backStack ->
                val presetId = backStack.toRoute<Route.Brewing>().presetId
                val pendingBrew by appViewModel.pendingBrew.collectAsStateWithLifecycle()
                val preset = if (presetId != null) {
                    state.presets.firstOrNull { it.id == presetId }
                } else {
                    pendingBrew
                }
                BrewingScreen(
                    preset = preset,
                    device = state.pairedDevice,
                    catalog = catalog,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
    } // JurasTheme
}

@Composable
private fun JurasBottomBar(navController: NavController, currentDestination: NavDestination?) {
    NavigationBar {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentDestination.isRoute(tab.route),
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = tab.icon,
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavDestination?.isRoute(route: Route): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true
