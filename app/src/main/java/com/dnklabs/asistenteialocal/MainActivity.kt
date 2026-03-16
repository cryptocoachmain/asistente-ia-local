package com.dnklabs.asistenteialocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dnklabs.asistenteialocal.ui.navigation.AppNavigation
import com.dnklabs.asistenteialocal.ui.theme.AsistenteIALocalTheme
import com.dnklabs.asistenteialocal.data.local.SecurityManager
import com.dnklabs.asistenteialocal.data.local.ModelPersistenceManager
import com.dnklabs.asistenteialocal.data.local.LicenseManager
import com.dnklabs.asistenteialocal.data.repository.LlamaCppRepository
import com.dnklabs.asistenteialocal.ui.screens.onboarding.ModelOption

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var modelPersistence: ModelPersistenceManager
    private lateinit var licenseManager: LicenseManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        securityManager = SecurityManager(this)
        modelPersistence = ModelPersistenceManager(this)
        licenseManager = LicenseManager(this)
        
        // Registrar primer uso (anti-manipulación)
        licenseManager.registerFirstUse()
        
        setContent {
            AsistenteIALocalTheme {
                AppNavigation(
                    securityManager = securityManager,
                    hasPin = securityManager.isPinConfigured(),
                    isFirstLaunch = !securityManager.isPinConfigured(),
                    isLicenseValid = licenseManager.isLicenseValid(),
                    licenseManager = licenseManager,
                    onPinVerified = { /* PIN verified */ },
                    onSetPin = { pin -> securityManager.setupPin(pin) },
                    onModelDownloaded = { modelOption ->
                        // Guardar el modelo seleccionado cuando el usuario lo descarga en onboarding
                        // ChatViewModel se encargará de inicializarlo cuando sea necesario
                        val modelFileName = getModelFileName(modelOption)
                        modelPersistence.saveSelectedModel(modelFileName)
                    },
                    freeSpaceGB = 5.0f
                )
            }
        }
    }
    
    private fun getModelFileName(modelOption: ModelOption): String {
        return when (modelOption) {
            ModelOption.QWEN_0_5B -> LlamaCppRepository.MODEL_QWEN_0_5B
            ModelOption.QWEN_1_5B -> LlamaCppRepository.MODEL_QWEN_1_5B
            ModelOption.QWEN_3B -> LlamaCppRepository.MODEL_QWEN_3B
            ModelOption.LLAMA_3_2_3B -> LlamaCppRepository.MODEL_LLAMA_3_2_3B
            ModelOption.GEMMA_2B -> LlamaCppRepository.MODEL_GEMMA_2B
            ModelOption.PHI_3_MINI -> LlamaCppRepository.MODEL_PHI_3_MINI
            ModelOption.MISTRAL_7B -> LlamaCppRepository.MODEL_MISTRAL_7B
            ModelOption.LLAMA_3_8B -> LlamaCppRepository.MODEL_LLAMA_3_8B
        }
    }
}
