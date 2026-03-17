package com.dnklabs.asistenteialocal.ui.screens.onboarding

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dnklabs.asistenteialocal.R
import com.dnklabs.asistenteialocal.data.local.LicenseManager

@Composable
fun WelcomeVideoScreen(
    onVideoFinished: () -> Unit
) {
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    val videoUri = remember {
        Uri.parse("android.resource://${context.packageName}/${R.raw.dnkwelcome}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                licenseManager.setVideoShown()
                onVideoFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(videoUri)
                    setOnCompletionListener {
                        licenseManager.setVideoShown()
                        onVideoFinished()
                    }
                    setOnErrorListener { _, _, _ ->
                        licenseManager.setVideoShown()
                        onVideoFinished() // Skip on error
                        true
                    }
                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
