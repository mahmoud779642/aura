package com.auracam.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class ThermalThrottlingLevel {
    NORMAL,
    MODERATE,
    SEVERE,
    CRITICAL
}

interface ThermalRepository {
    val throttlingLevel: StateFlow<ThermalThrottlingLevel>
    fun unregister()
}
