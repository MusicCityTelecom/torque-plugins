package com.musiccitytelecom.torque.c5diag

import android.content.Context

data class PluginSettings(
    val vin: String,
    val webhookUrl: String,
    val webhookSecret: String,
    val useP59C1C2Swap: Boolean
)

object SettingsStore {
    private const val PREFS = "c5_diag_settings"
    private const val VIN = "vin"
    private const val WEBHOOK_URL = "webhook_url"
    private const val WEBHOOK_SECRET = "webhook_secret"
    private const val P59_SWAP = "p59_c1_c2_swap"

    fun load(context: Context): PluginSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PluginSettings(
            vin = p.getString(VIN, "") ?: "",
            webhookUrl = p.getString(WEBHOOK_URL, "") ?: "",
            webhookSecret = p.getString(WEBHOOK_SECRET, "") ?: "",
            useP59C1C2Swap = p.getBoolean(P59_SWAP, true)
        )
    }

    fun save(context: Context, settings: PluginSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(VIN, settings.vin.trim().uppercase())
            .putString(WEBHOOK_URL, settings.webhookUrl.trim())
            .putString(WEBHOOK_SECRET, settings.webhookSecret)
            .putBoolean(P59_SWAP, settings.useP59C1C2Swap)
            .apply()
    }
}
