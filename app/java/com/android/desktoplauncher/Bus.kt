package com.android.desktoplauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Bus {
    var dots by mutableStateOf(setOf<String>())
    var drawerRequest by mutableStateOf(0)
}
