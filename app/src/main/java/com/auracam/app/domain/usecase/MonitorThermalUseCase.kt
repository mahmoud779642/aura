package com.auracam.app.domain.usecase

import com.auracam.app.domain.repository.ThermalRepository
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import kotlinx.coroutines.flow.StateFlow

class MonitorThermalUseCase(private val thermalRepository: ThermalRepository) {
    val currentLevel: StateFlow<ThermalThrottlingLevel> = thermalRepository.throttlingLevel
    
    fun shutdown() {
        thermalRepository.unregister()
    }
}
