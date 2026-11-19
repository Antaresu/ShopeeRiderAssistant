package com.rider.shopeeassistant.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object HuaweiSettingsHelper {

    /**
     * Checks if current device is Huawei / Honor (e.g. Huawei Nova 5T)
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
    }

    /**
     * Opens EMUI "Manage Manually" App Launch settings directly
     */
    fun openHuaweiAppLaunchSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Hanapin ang Shopee Assistant -> I-set sa 'Manage Manually' (I-ON lahat ng switches)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fallback to standard battery optimization screen
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        }
    }
}
