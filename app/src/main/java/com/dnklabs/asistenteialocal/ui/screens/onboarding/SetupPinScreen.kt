package com.dnklabs.asistenteialocal.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnklabs.asistenteialocal.data.local.SecurityManager
import com.dnklabs.asistenteialocal.ui.theme.LocalErrorRed
import com.dnklabs.asistenteialocal.ui.theme.LocalPrivacyGreen
import com.dnklabs.asistenteialocal.ui.theme.LocalWarningOrange

/**
 * Pantalla de configuración de PIN durante el onboarding
 * Permite crear PIN de 4-6 dígitos con confirmación
 * Incluye aviso obligatorio sobre pérdida de PIN
 */
@Composable
fun SetupPinScreen(
    securityManager: SecurityManager,
    onPinSetupComplete: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) } // 0: Aviso, 1: Crear PIN, 2: Confirmar PIN
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWarningDialog by remember { mutableStateOf(true) } // Mostrar al inicio
    
    // Diálogo de aviso obligatorio
    if (showWarningDialog && step == 0) {
        WarningDialog(
            onDismiss = { 
                showWarningDialog = false
                step = 1
            },
            onGoBack = onBackClick
        )
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header con botón atrás
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when (step) {
                        1 -> onBackClick()
                        2 -> {
                            step = 1
                            pin = ""
                            confirmPin = ""
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Atrás"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Icono y título
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            if (step == 2) LocalPrivacyGreen.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (step == 2) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (step == 2) LocalPrivacyGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = when (step) {
                        1 -> "Crea tu PIN de acceso"
                        2 -> "Confirma tu PIN"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (step) {
                        1 -> "Elige un PIN de 4 a 6 dígitos"
                        2 -> "Vuelve a introducir tu PIN para confirmar"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Indicador de PIN
            val currentPin = if (step == 2) confirmPin else pin
            PinIndicator(
                pinLength = currentPin.length,
                maxLength = 6,
                isError = errorMessage != null,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // Mensaje de error
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = LocalErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Teclado numérico
            if (step in 1..2) {
                NumericKeypad(
                    currentLength = currentPin.length,
                    onDigitClick = { digit ->
                        errorMessage = null
                        if (currentPin.length < 6) {
                            if (step == 1) {
                                pin += digit
                            } else {
                                confirmPin += digit
                            }
                        }
                    },
                    onBackspaceClick = {
                        errorMessage = null
                        if (step == 1 && pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                        } else if (step == 2 && confirmPin.isNotEmpty()) {
                            confirmPin = confirmPin.dropLast(1)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botón continuar
                Button(
                    onClick = {
                        when (step) {
                            1 -> {
                                if (pin.length < 4) {
                                    errorMessage = "El PIN debe tener al menos 4 dígitos"
                                } else {
                                    step = 2
                                    errorMessage = null
                                }
                            }
                            2 -> {
                                if (pin != confirmPin) {
                                    errorMessage = "Los PINs no coinciden. Intenta de nuevo."
                                    confirmPin = ""
                                } else {
                                    // Guardar PIN
                                    if (securityManager.setupPin(pin)) {
                                        securityManager.markSetupComplete()
                                        onPinSetupComplete()
                                    } else {
                                        errorMessage = "Error al guardar el PIN"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = when (step) {
                        1 -> pin.length >= 4
                        2 -> confirmPin.length == pin.length
                        else -> false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (step == 1) "Continuar" else "Confirmar PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (step == 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null
                        )
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WarningDialog(
    onDismiss: () -> Unit,
    onGoBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = LocalWarningOrange,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "¡Importante!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Tu PIN es la única forma de acceder a la aplicación y a tus datos.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LocalErrorRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Si olvidas tu PIN:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = LocalErrorRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• No podrás recuperar tus conversaciones\n" +
                                   "• No podrás recuperar tus documentos\n" +
                                   "• Tendrás que reinstalar la aplicación\n" +
                                   "• Todos tus datos se perderán",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Asegúrate de recordar tu PIN o guárdalo en un lugar seguro.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPrivacyGreen
                )
            ) {
                Text("Entendido, continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onGoBack) {
                Text("Volver atrás")
            }
        }
    )
}

@Composable
private fun PinIndicator(
    pinLength: Int,
    maxLength: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(maxLength) { index ->
            val isFilled = index < pinLength
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isError -> LocalErrorRed
                            isFilled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    currentLength: Int,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Filas 1-3: dígitos 1-9
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) { col ->
                    val digit = (row * 3 + col + 1).toString()
                    KeypadButton(
                        text = digit,
                        onClick = { onDigitClick(digit) },
                        enabled = currentLength < 6,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }
        
        // Fila 4: vacío, 0, borrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(72.dp))
            
            // Botón 0
            KeypadButton(
                text = "0",
                onClick = { onDigitClick("0") },
                enabled = currentLength < 6,
                modifier = Modifier.size(72.dp)
            )
            
            // Botón borrar
            IconButton(
                onClick = onBackspaceClick,
                enabled = currentLength > 0,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Borrar",
                    modifier = Modifier.size(28.dp),
                    tint = if (currentLength > 0) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
