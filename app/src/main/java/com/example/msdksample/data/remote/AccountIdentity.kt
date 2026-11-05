package com.example.msdksample.data.remote

import android.content.Context
import com.example.msdksample.utils.PreferenceManager

object AccountIdentity {
    fun owner(context: Context): String {
        BackendSessions.registry.current()?.let { return "user:${it.userId}" }
        val prefs=PreferenceManager.getInstance(context)
        return if(prefs.isLoggedIn() && prefs.getUserPhone().isNotBlank()) "phone:${prefs.getUserPhone()}" else "guest"
    }
}
