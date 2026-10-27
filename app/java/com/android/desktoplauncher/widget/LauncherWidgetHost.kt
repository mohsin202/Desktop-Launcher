package com.android.desktoplauncher.widget

import android.appwidget.AppWidgetHost
import android.content.Context

class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    companion object {
        const val HOST_ID = 2048
    }
}
