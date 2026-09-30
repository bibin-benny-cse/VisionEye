package com.bibin.visioneye.data.repository

import com.bibin.visioneye.data.model.UserPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Architectural contract for managing user settings and accessibility preferences.
 *
 * (DataStore / Room persistence to be integrated in future milestone)
 */
interface SettingsRepository {
    /**
     * Observable stream of current user preferences.
     */
    val preferences: StateFlow<UserPreferences>

    /**
     * Updates stored user preferences.
     */
    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences)
}
