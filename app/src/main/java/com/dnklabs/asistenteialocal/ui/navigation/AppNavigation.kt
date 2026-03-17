package com.dnklabs.asistenteialocal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dnklabs.asistenteialocal.ui.screens.chat.ChatScreen
import com.dnklabs.asistenteialocal.ui.screens.history.HistoryScreen
import com.dnklabs.asistenteialocal.ui.screens.license.LicenseExpiredScreen
import com.dnklabs.asistenteialocal.ui.screens.license.LicenseActivationScreen
import com.dnklabs.asistenteialocal.ui.screens.lock.LockScreen
import com.dnklabs.asistenteialocal.ui.screens.ocr.OCRScreen
import com.dnklabs.asistenteialocal.ui.screens.onboarding.ModelOption
import com.dnklabs.asistenteialocal.ui.screens.onboarding.ModelSelectionScreen
import com.dnklabs.asistenteialocal.ui.screens.onboarding.PrivacyScreen
import com.dnklabs.asistenteialocal.ui.screens.onboarding.SetupPinScreen
import com.dnklabs.asistenteialocal.ui.screens.onboarding.WelcomeScreen
import com.dnklabs.asistenteialocal.ui.screens.settings.SettingsScreen
import com.dnklabs.asistenteialocal.ui.screens.settings.ChangePinScreen
import com.dnklabs.asistenteialocal.ui.screens.settings.ChangeModelScreen
import com.dnklabs.asistenteialocal.ui.screens.onboarding.WelcomeVideoScreen
import com.dnklabs.asistenteialocal.data.local.LicenseManager
import com.dnklabs.asistenteialocal.data.repository.UpdateRepository
import com.dnklabs.asistenteialocal.data.repository.UpdateInfo

sealed class Screen(val route: String) {
    object Lock : Screen("lock")
    object OnboardingWelcome : Screen("onboarding_welcome")
    object OnboardingPrivacy : Screen("onboarding_privacy")
    object OnboardingSetupPin : Screen("onboarding_setup_pin")
    object OnboardingModel : Screen("onboarding_model")
    object Chat : Screen("chat?chatId={chatId}") {
        fun createRoute(chatId: String? = null): String {
            return if (chatId != null) "chat?chatId=$chatId" else "chat"
        }
    }
    object History : Screen("history")
    object Settings : Screen("settings")
    object OCR : Screen("ocr")
    object ChangePin : Screen("change_pin")
    object ChangeModel : Screen("change_model")
    object Splash : Screen("splash")
    object LicenseExpired : Screen("license_expired")
    object LicenseActivation : Screen("license_activation")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    securityManager: com.dnklabs.asistenteialocal.data.local.SecurityManager,
    hasPin: Boolean = false,
    isFirstLaunch: Boolean = true,
    isLicenseValid: Boolean = true,
    licenseManager: LicenseManager? = null,
    onPinVerified: () -> Unit = {},
    onSetPin: (String) -> Unit = {},
    onModelDownloaded: (ModelOption) -> Unit = {},
    freeSpaceGB: Float = 5.0f
) {
    var onboardingStep by remember { mutableStateOf(0) }
    
    // Verificar si necesita activación de licencia
    val needsLicenseActivation = licenseManager?.let { !it.hasLicenseKey() } ?: false

    // Determinar destino inicial basado en licencia
    val startDestination = remember(isLicenseValid, needsLicenseActivation) {
        when {
            !isLicenseValid -> Screen.LicenseExpired.route
            needsLicenseActivation -> Screen.LicenseActivation.route
            else -> Screen.Splash.route
        }
    }

    val nextDestination = remember(hasPin, isFirstLaunch, isLicenseValid, needsLicenseActivation) {
        when {
            !isLicenseValid -> Screen.LicenseExpired.route
            needsLicenseActivation -> Screen.LicenseActivation.route
            isFirstLaunch -> Screen.OnboardingWelcome.route
            hasPin -> Screen.Lock.route
            else -> Screen.Chat.route
        }
    }


    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Pantalla de licencia expirada
        composable(Screen.LicenseExpired.route) {
            LicenseExpiredScreen(
                onUpdateAvailable = { _ ->
                    // La descarga se maneja dentro del LicenseExpiredScreen
                },
                onRetryCheck = {
                    // Re-intentar verificación
                }
            )
        }
        
        // Pantalla de activación de licencia
        composable(Screen.LicenseActivation.route) {
            LicenseActivationScreen(
                onLicenseActivated = {
                    navController.navigate(Screen.Splash.route) {
                        popUpTo(Screen.LicenseActivation.route) { inclusive = true }
                    }
                }
            )
        }

        // Pantalla de video de bienvenida (Splash)
        composable(Screen.Splash.route) {
            WelcomeVideoScreen(
                onVideoFinished = {
                    navController.navigate(nextDestination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Pantalla de bloqueo PIN

        composable(Screen.Lock.route) {
            LockScreen(
                securityManager = securityManager,
                onPinCorrect = {
                    onPinVerified()
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding - Pantalla 1: Bienvenida
        composable(Screen.OnboardingWelcome.route) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Screen.OnboardingPrivacy.route)
                }
            )
        }

        // Onboarding - Pantalla 2: Privacidad
        composable(Screen.OnboardingPrivacy.route) {
            PrivacyScreen(
                onContinue = {
                    navController.navigate(Screen.OnboardingSetupPin.route)
                }
            )
        }

        // Onboarding - Pantalla 3: Configuración de PIN
        composable(Screen.OnboardingSetupPin.route) {
            SetupPinScreen(
                securityManager = securityManager,
                onPinSetupComplete = {
                    navController.navigate(Screen.OnboardingModel.route)
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // Onboarding - Pantalla 4: Selección de modelo
        composable(Screen.OnboardingModel.route) {
            ModelSelectionScreen(
                freeSpaceGB = freeSpaceGB,
                onDownloadAndInstall = { modelOption ->
                    onModelDownloaded(modelOption)
                    // Después de descargar, ir al chat
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                    }
                }
            )
        }

        // Pantalla principal de chat
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                androidx.navigation.navArgument("chatId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            ChatScreen(
                chatId = chatId,
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToOCR = {
                    navController.navigate(Screen.OCR.route)
                }
            )
        }

        // Historial de conversaciones
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onChatSelected = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                },
                onNewChat = {
                    navController.navigate(Screen.Chat.createRoute(null)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                }
            )
        }

        // Configuración
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSetPin = { pin ->
                    onSetPin(pin)
                },
                onChangePin = {
                    navController.navigate(Screen.ChangePin.route)
                },
                onChangeModel = {
                    navController.navigate(Screen.ChangeModel.route)
                }
            )
        }

        // Cambiar PIN
        composable(Screen.ChangePin.route) {
            ChangePinScreen(
                securityManager = securityManager,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPinChanged = {
                    navController.popBackStack()
                }
            )
        }

        // Cambiar Modelo
        composable(Screen.ChangeModel.route) {
            ChangeModelScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onModelSelected = { modelOption ->
                    onModelDownloaded(modelOption)
                    navController.popBackStack()
                }
            )
        }

        // OCR / Cámara
        composable(Screen.OCR.route) {
            OCRScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTextRecognized = { text ->
                    navController.popBackStack()
                    // Enviar texto al chat
                }
            )
        }
    }
}
