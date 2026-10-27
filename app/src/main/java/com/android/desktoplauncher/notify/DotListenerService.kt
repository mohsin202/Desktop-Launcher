package com.android.desktoplauncher.notify

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.android.desktoplauncher.Bus

class DotListenerService : NotificationListenerService() {
    override fun onListenerConnected() = publish()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val pkgs = runCatching { activeNotifications.map { it.packageName }.toSet() }.getOrDefault(emptySet())
        Handler(Looper.getMainLooper()).post { Bus.dots = pkgs }
    }
}
