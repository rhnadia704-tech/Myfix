package com.example

import android.Manifest
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MYFixScreen
import com.example.ui.MYFixViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.MYFixTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MYFixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var showSettings by rememberSaveable { mutableStateOf(false) }

            val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    viewModel.exportToSdcard()
                }
            }

            val manageStorageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                viewModel.exportToSdcard()
            }

            val importBinaryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.importBinaryFromUri(uri)
                }
            }

            MYFixTheme {
                AnimatedContent(
                    targetState = showSettings,
                    label = "myfix_screen_transition",
                ) { inSettings ->
                    if (inSettings) {
                        SettingsScreen(
                            activeBinary = state.status.activeBinary,
                            onResetToBundled = { viewModel.resetToBundledBinary() },
                            onBack = { showSettings = false },
                        )
                    } else {
                        MYFixScreen(
                            state = state,
                            onDiagnosticClick = { viewModel.runDiagnosticDeployed() },
                            onDiagnosticWithoutDeployClick = { viewModel.runDiagnosticWithoutDeploy() },
                            onDeployClick = { viewModel.deployToSystem() },
                            onExportSdcardClick = { viewModel.exportToSdcard() },
                            onImportClick = {
                                importBinaryLauncher.launch(arrayOf("*/*"))
                            },
                            onTestFingerprintLiveClick = {
                                launchLiveFingerprintTest()
                            },
                            onOpenFingerprintSettingsClick = {
                                openFingerprintEnrollmentSettings()
                            },
                            onStopLiveClick = { viewModel.stopLiveMonitor() },
                            onRefreshStatusClick = { viewModel.refreshSystemStatus() },
                            onOpenSettingsClick = { showSettings = true },
                            onClearLogsClick = { viewModel.clearLogs() },
                            onDismissBanner = { viewModel.dismissBanner() },
                            onRequestStoragePermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:$packageName"),
                                    )
                                    manageStorageLauncher.launch(intent)
                                } else {
                                    legacyStoragePermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    )
                                }
                            },
                            onDismissStorageDialog = { viewModel.dismissStorageDialog() },
                        )
                    }
                }
            }
        }
    }

    private fun launchLiveFingerprintTest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            viewModel.showTransientBanner("BiometricPrompt requiert Android 9+", isError = true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = getSystemService(BiometricManager::class.java)
            val status = bm?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
            )
            if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                viewModel.showTransientBanner(
                    "Aucune empreinte enregistrée — ouvrez Paramètres empreinte",
                    isError = false,
                )
                openFingerprintEnrollmentSettings()
                return
            }
        }

        val cancellationSignal = CancellationSignal()
        val builder = BiometricPrompt.Builder(this)
            .setTitle("Test capteur FOD (Live)")
            .setSubtitle("Posez le doigt pour observer libfodfix en direct")
            .setNegativeButton("Fermer", mainExecutor) { _, _ ->
                cancellationSignal.cancel()
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
            )
        }

        builder.build().authenticate(
            cancellationSignal,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    ) {
                        viewModel.showTransientBanner(
                            "Capteur: ${errString ?: errorCode}",
                            isError = true,
                        )
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    viewModel.showTransientBanner("Interaction empreinte validée")
                }

                override fun onAuthenticationFailed() {
                    viewModel.showTransientBanner("Empreinte non reconnue")
                }
            },
        )
    }

    private fun openFingerprintEnrollmentSettings() {
        val candidates = listOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            } else {
                @Suppress("DEPRECATION")
                Intent(Settings.ACTION_FINGERPRINT_ENROLL)
            },
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (intent in candidates) {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        }
        viewModel.showTransientBanner("Impossible d'ouvrir les paramètres biométriques", isError = true)
    }
}
