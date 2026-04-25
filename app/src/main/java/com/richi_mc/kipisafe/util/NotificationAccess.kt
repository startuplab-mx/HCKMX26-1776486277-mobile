package com.richi_mc.kipisafe.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Comprueba si algún componente de esta app está habilitado como
 * [android.service.notification.NotificationListenerService].
 */
fun Context.isNotificationServiceEnabled(): Boolean {
    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?: return false
    if (flat.isEmpty()) return false
    val packageName = packageName
    for (name in flat.split(":")) {
        val component = ComponentName.unflattenFromString(name) ?: continue
        if (TextUtils.equals(packageName, component.packageName)) {
            return true
        }
    }
    return false
}
