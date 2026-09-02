package com.lagradost.cloudstream3.ui.tv

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Holds the TV-only Compose rollout switch in one place so the legacy TV surface remains the
 * safe default while the new navigation bridge is being validated.
 */
object TvExperienceSettings {
    const val KEY = "tv_compose_experience_enabled"
    const val DEFAULT_ENABLED = false

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(KEY, enabled)
        }
    }
}
