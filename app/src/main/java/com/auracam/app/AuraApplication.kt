package com.auracam.app

import android.app.Application
import android.util.Log

class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("auracam")
            Log.i("AuraApplication", "Native AuraCam library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("AuraApplication", "Failed to load native AuraCam library: ${e.message}")
        }
    }
}
