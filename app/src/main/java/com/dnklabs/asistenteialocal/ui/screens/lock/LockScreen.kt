package com.dnklabs.asistenteialocal.ui.screens.lock

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

/**
 * Pantalla de bloqueo con teclado numérico PIN
 * Muestra intentos restantes y bloqueo temporal después de 5 fallos
 */
@Composable
fun LockScreen(
    securityManager: SecurityManager,
    onPinCorrect: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var isLockedOut by remember { mutableStateOf(securityManager.isLockedOut()) }
    var lockoutSeconds by remember { mutableIntStateOf(securityManager.getLockoutRemainingSeconds()) }
    var remainingAttempts by remember { mutableIntStateOf(securityManager.getRemainingAttempts()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // Actualizar temporizador de bloqueo
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (lockoutSeconds > 0) {
                delay(1000)
                lockoutSeconds = securityManager.getLockoutRemainingSeconds()
                if (lockoutSeconds <= 0) {
                    isLockedOut = false
                    remainingAttempts = securityManager.getRemainingAttempts()
                }
            }
        }
    }
    
    // Limpiar mensaje de error después de 2 segundos
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(2000)
            errorMessage = null
        }
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueado",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Introduce tu PIN para acceder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Estado de bloqueo o intentos restantes
                when {
                    isLockedOut -> {
                        Text(
                            text = "Bloqueado temporalmente",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalErrorRed,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Espera ${formatTime(lockoutSeconds)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    remainingAttempts < 5 -> {
                        Text(
                            text = "Intentos restantes: $remainingAttempts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (remainingAttempts <= 2) LocalErrorRed 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Indicador de PIN
            PinIndicator(
                pinLength = pin.length,
                maxLength = 4,
                isError = errorMessage != null,
                modifier = Modifier.padding(vertical = 32.dp)
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
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Teclado numérico
            if (!isLockedOut) {
                NumericKeypad(
                    onDigitClick = { digit ->
                        if (pin.length < 4 && !isVerifying) {
                            pin += digit
                            
                            // Auto-verificar cuando tiene 4 dígitos
                            if (pin.length == 4) {
                                isVerifying = true
                                if (securityManager.verifyPin(pin)) {
                                    onPinCorrect()
                                } else {
                                    errorMessage = "PIN incorrecto"
                                    pin = ""
                                    remainingAttempts = securityManager.getRemainingAttempts()
                                    isLockedOut = securityManager.isLockedOut()
                                    if (isLockedOut) {
                                        lockoutSeconds = securityManager.getLockoutRemainingSeconds()
                                    }
                                }
                                isVerifying = false
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (pin.isNotEmpty() && !isVerifying) {
                            pin = pin.dropLast(1)
                        }
                    },
                    onBiometricClick = onBiometricClick,
                    showBiometric = onBiometricClick != null,
                    isEnabled = !isVerifying
                )
            } else {
                // Mostrar cuenta regresiva cuando está bloqueado
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = formatTime(lockoutSeconds),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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
                    .size(16.dp)
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
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    showBiometric: Boolean,
    isEnabled: Boolean,
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
                        enabled = isEnabled,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }
        
        // Fila 4: huella, 0, borrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de huella dactilar (solo si está disponible)
            if (showBiometric) {
                IconButton(
                    onClick = { onBiometricClick?.invoke() },
                    enabled = isEnabled,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Usar huella dactilar",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }
            
            // Botón 0
            KeypadButton(
                text = "0",
                onClick = { onDigitClick("0") },
                enabled = isEnabled,
                modifier = Modifier.size(72.dp)
            )
            
            // Botón borrar
            IconButton(
                onClick = onBackspaceClick,
                enabled = isEnabled,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Borrar",
                    modifier = Modifier.size(28.dp),
                    tint = if (isEnabled) MaterialTheme.colorScheme.onSurface 
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

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
