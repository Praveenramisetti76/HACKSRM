package com.example.healthpro.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.healthpro.auth.AuthPreferences
import com.example.healthpro.auth.AuthScreenState
import com.example.healthpro.auth.AuthViewModel
import com.example.healthpro.auth.ui.EmailScreen
import com.example.healthpro.auth.ui.NameSetupScreen
import com.example.healthpro.auth.ui.OtpScreen
import com.example.healthpro.screens.*
import com.example.healthpro.ui.callfamily.CallFamilyScreenNew
import com.example.healthpro.ui.help.HelpScreenNew
import com.example.healthpro.medicine.ui.*
import com.example.healthpro.ui.memories.MemoriesScreenNew

import com.example.healthpro.ui.theme.BottomBarDark
import com.example.healthpro.ui.theme.DarkNavy
import com.example.healthpro.ui.theme.TealAccent
import com.example.healthpro.ui.theme.TextMuted
import com.example.healthpro.ble.BleStateHolder

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Memories : Screen("memories")
    object Genie : Screen("genie")
    object FoodOrder : Screen("food_order")
    object CallFamily : Screen("call_family")
    object Emergency : Screen("emergency")
    object Inactivity : Screen("inactivity")
    object Settings : Screen("settings")
    object MedicineOrder : Screen("medicine_order")
    // ─── NEW ROUTES ───

    object Medicine : Screen("medicine")
    // ─── BLE Fall Detection ───
    object BlePairing : Screen("ble_pairing")
    object FallAlert  : Screen("fall_alert")
    // ─── AUTH ROUTES ───
    object AuthEmail : Screen("auth_email")
    object AuthOtp : Screen("auth_otp")
    object AuthNameSetup : Screen("auth_name_setup")
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Default.Home, Screen.Home.route),
    BottomNavItem("Photos", Icons.Default.Photo, Screen.Memories.route),
    BottomNavItem("Settings", Icons.Default.Settings, Screen.Settings.route)
)

@Composable
fun SahayNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check auth state to determine start destination
    val context = LocalContext.current
    val authPreferences = remember { AuthPreferences(context) }
    val startDestination = if (authPreferences.isLoggedIn) {
        Screen.Home.route
    } else {
        Screen.AuthEmail.route
    }

    // ─── Global FALL_DETECTED observer ───────────────────────────────────
    // Listens to BleStateHolder from anywhere in the app and jumps straight
    // to FallAlertScreen when the background FallDetectionService fires.
    val fallAlertPending by BleStateHolder.fallAlertPending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(fallAlertPending) {
        if (fallAlertPending) {
            navController.navigate(Screen.FallAlert.route) {
                launchSingleTop = true
            }
        }
    }

    // Auth screens should NOT show bottom bar
    val authRoutes = listOf(
        Screen.AuthEmail.route,
        Screen.AuthOtp.route,
        Screen.AuthNameSetup.route
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Memories.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SahayBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
        containerColor = DarkNavy
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavy)
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                // ═══════════════════════════════════════════
                // AUTH SCREENS (shared ViewModel)
                // ═══════════════════════════════════════════

                composable(Screen.AuthEmail.route) {
                    // Scope AuthViewModel to the Activity so it survives between auth screens
                    val activity = LocalContext.current as androidx.activity.ComponentActivity
                    val authViewModel: AuthViewModel = viewModel(viewModelStoreOwner = activity)
                    val authState by authViewModel.authState.collectAsState()

                    // Navigate to OTP screen when OTP is sent
                    androidx.compose.runtime.LaunchedEffect(authState) {
                        if (authState is AuthScreenState.OtpSent) {
                            navController.navigate(Screen.AuthOtp.route) {
                                launchSingleTop = true
                            }
                        }
                    }

                    EmailScreen(
                        authState = authState,
                        onSubmitEmail = { authViewModel.submitEmail(it) },
                        onClearError = { authViewModel.clearError() }
                    )
                }

                composable(Screen.AuthOtp.route) {
                    val activity = LocalContext.current as androidx.activity.ComponentActivity
                    val authViewModel: AuthViewModel = viewModel(viewModelStoreOwner = activity)
                    val authState by authViewModel.authState.collectAsState()

                    // Get the test OTP from state
                    val testOtp = (authState as? AuthScreenState.OtpSent)?.otp ?: ""

                    // Navigate to name setup when OTP is verified
                    androidx.compose.runtime.LaunchedEffect(authState) {
                        if (authState is AuthScreenState.OtpVerified) {
                            navController.navigate(Screen.AuthNameSetup.route) {
                                launchSingleTop = true
                            }
                        }
                    }

                    OtpScreen(
                        authState = authState,
                        testOtp = testOtp,
                        onVerifyOtp = { authViewModel.verifyOtp(it) },
                        onResendOtp = { authViewModel.resendOtp() },
                        getResendCooldown = { authViewModel.getResendCooldown() },
                        onClearError = { authViewModel.clearError() }
                    )
                }

                composable(Screen.AuthNameSetup.route) {
                    val activity = LocalContext.current as androidx.activity.ComponentActivity
                    val authViewModel: AuthViewModel = viewModel(viewModelStoreOwner = activity)
                    val authState by authViewModel.authState.collectAsState()

                    // Navigate to home when setup is complete
                    androidx.compose.runtime.LaunchedEffect(authState) {
                        if (authState is AuthScreenState.SetupComplete) {
                            navController.navigate(Screen.Home.route) {
                                // Clear the entire auth backstack
                                popUpTo(Screen.AuthEmail.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    NameSetupScreen(
                        authState = authState,
                        onSubmitName = { authViewModel.submitName(it) },
                        onClearError = { authViewModel.clearError() }
                    )
                }

                // ═══════════════════════════════════════════
                // MAIN APP SCREENS (UNCHANGED)
                // ═══════════════════════════════════════════

                composable(Screen.Home.route) {
                    HomeScreen(navController = navController)
                }
                composable(Screen.Memories.route) {
                    // ─── NEW: Full Memories with location-based albums ───
                    MemoriesScreenNew(navController = navController)
                }
                composable(Screen.Genie.route) {
                    GenieScreen(navController = navController)
                }
                composable(Screen.FoodOrder.route) {
                    FoodOrderScreen(navController = navController)
                }
                composable(Screen.CallFamily.route) {
                    // ─── Full Call Family module with contact picker ───
                    CallFamilyScreenNew(navController = navController)
                }
                composable(Screen.Emergency.route) {
                    // ─── Full Help/SOS module with emergency system ───
                    HelpScreenNew(navController = navController)
                }
                composable(Screen.Inactivity.route) {
                    InactivityScreen(navController = navController)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
                composable(Screen.MedicineOrder.route) {
                    MedicineOrderScreen(navController = navController)
                }
                // ─── NEW SCREENS ───

                composable(Screen.Medicine.route) {
                    PrescriptionVaultScreen(navController = navController)
                }
                composable("medicine_detail/{medicineId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("medicineId")?.toLongOrNull() ?: 0L
                    MedicineDetailScreen(navController = navController, medicineId = id)
                }
                composable("medicine_intake") {
                    IntakeTrackerScreen(navController = navController)
                }
                composable("medicine_reorder/{medicineId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("medicineId")?.toLongOrNull() ?: 0L
                    ReorderConfirmationScreen(navController = navController, medicineId = id)
                }
                composable("medicine_order_status/{medicineId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("medicineId")?.toLongOrNull() ?: 0L
                    OrderStatusScreen(navController = navController, medicineId = id)
                }
                // ─── BLE Fall Detection screens ───
                composable(Screen.BlePairing.route) {
                    BleDevicePairingScreen(navController = navController)
                }
                composable(Screen.FallAlert.route) {
                    FallAlertScreen(navController = navController)
                }
            }
        }
    }
}

@Composable
fun SahayBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = BottomBarDark,
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TealAccent,
                    selectedTextColor = TealAccent,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
