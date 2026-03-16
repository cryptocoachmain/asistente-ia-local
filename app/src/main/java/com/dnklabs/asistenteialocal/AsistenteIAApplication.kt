package com.dnklabs.asistenteialocal

import android.app.Application
import com.dnklabs.asistenteialocal.data.local.AppLogger

class AsistenteIAApplication : Application() {
    
    init {
        // Cargar biblioteca nativa de llama.cpp
        System.loadLibrary("ailocal")
    }
    
    override fun onCreate() {
        super.onCreate()
        // Inicializar logger de archivo
        AppLogger.init(this)
        AppLogger.i("Application", "Asistente de IA Local iniciado")
    }
}
