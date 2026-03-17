package com.dnklabs.asistenteialocal.ui.screens.license

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dnklabs.asistenteialocal.data.local.LicenseManager

@Composable
fun LicenseActivationScreen(
    onLicenseActivated: () -> Unit
) {
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Si ya tiene licencia guardada, continuar
    LaunchedEffect(Unit) {
        if (licenseManager.hasLicenseKey()) {
            onLicenseActivated()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icono
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Título
                Text(
                    text = "Activación Requerida",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Subtítulo
                Text(
                    text = "Introduce la contraseña de activación para usar la app",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Campo de contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Contraseña") },
                    placeholder = { Text("Introduce tu contraseña") },
                    singleLine = true,
                    isError = errorMessage != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            isLoading = true
                            if (licenseManager.saveLicenseKey(password)) {
                                onLicenseActivated()
                            } else {
                                errorMessage = "Contraseña incorrecta"
                                isLoading = false
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Mensaje de error
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botón activar
                Button(
                    onClick = {
                        isLoading = true
                        if (licenseManager.saveLicenseKey(password)) {
                            onLicenseActivated()
                        } else {
                            errorMessage = "Contraseña incorrecta. Inténtalo de nuevo."
                            isLoading = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = password.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activar App")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Contacto
                Text(
                    text = "¿No tienes contraseña? Contacta con el desarrollador",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
