package com.auracam.app.data.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.auracam.app.domain.repository.ThermalRepository
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThermalRepositoryImpl(private val context: Context) : ThermalRepository {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private val _throttlingLevel = MutableStateFlow(ThermalThrottlingLevel.NORMAL)
    override val throttlingLevel: StateFlow<ThermalThrottlingLevel> = _throttlingLevel.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    init {
        registerThermalListener()
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                val level = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> ThermalThrottlingLevel.NORMAL
                    PowerManager.THERMAL_STATUS_LIGHT -> ThermalThrottlingLevel.NORMAL
                    PowerManager.THERMAL_STATUS_MODERATE -> ThermalThrottlingLevel.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> ThermalThrottlingLevel.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL -> ThermalThrottlingLevel.CRITICAL
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalThrottlingLevel.CRITICAL
                    else -> ThermalThrottlingLevel.NORMAL
                }
                
                if (_throttlingLevel.value != level) {
                    _throttlingLevel.value = level
                    Log.w("ThermalRepository", "Dynamic thermal status changed to: $level ($status)")
                }
            }.also {
                try {
                    powerManager.addOnThermalStatusListener(context.mainExecutor, it)
                } catch (e: Exception) {
                    Log.e("ThermalRepository", "Failed to register thermal status listener: ${e.message}")
                }
            }
        }
    }

    override fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                powerManager.removeOnThermalStatusListener(thermalListener!!)
            } catch (e: Exception) {
                Log.e("ThermalRepository", "Failed to unregister thermal listener: ${e.message}")
            }
        }
    }
}
