package com.dnklabs.asistenteialocal.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dnklabs.asistenteialocal.data.local.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit = {},
    onPinChanged: () -> Unit = {}
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar PIN") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Cambiar PIN de acceso",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = "Ingresa tu PIN actual y el nuevo PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current PIN
            OutlinedTextField(
                value = currentPin,
                onValueChange = { 
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        currentPin = it
                        errorMessage = null
                    }
                },
                label = { Text("PIN actual") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            
            // New PIN
            OutlinedTextField(
                value = newPin,
                onValueChange = { 
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        newPin = it
                        errorMessage = null
                    }
                },
                label = { Text("Nuevo PIN") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Confirm PIN
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { 
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        confirmPin = it
                        errorMessage = null
                    }
                },
                label = { Text("Confirmar nuevo PIN") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                
                Button(
                    onClick = {
                        // Validate
                        when {
                            currentPin.length < 4 -> {
                                errorMessage = "El PIN actual debe tener al menos 4 dígitos"
                            }
                            newPin.length < 4 -> {
                                errorMessage = "El nuevo PIN debe tener al menos 4 dígitos"
                            }
                            newPin != confirmPin -> {
                                errorMessage = "Los PINs no coinciden"
                            }
                            !securityManager.verifyPin(currentPin) -> {
                                errorMessage = "PIN actual incorrecto"
                            }
                            else -> {
                                // Save new PIN
                                securityManager.setupPin(newPin)
                                showSuccess = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = currentPin.isNotBlank() && newPin.isNotBlank() && confirmPin.isNotBlank()
                ) {
                    Text("Guardar")
                }
            }
        }
    }
    
    // Success dialog
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { 
                showSuccess = false
                onPinChanged()
            },
            title = { Text("¡Éxito!") },
            text = { Text("Tu PIN ha sido cambiado correctamente.") },
            confirmButton = {
                Button(
                    onClick = { 
                        showSuccess = false
                        onPinChanged()
                    }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }
}
