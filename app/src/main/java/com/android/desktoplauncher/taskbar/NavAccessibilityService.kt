package com.android.desktoplauncher.taskbar

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NavAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        var instance: NavAccessibilityService? = null

        fun enabled() = instance != null

        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        fun recents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
    }
}
