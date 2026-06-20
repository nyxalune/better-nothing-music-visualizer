package com.better.nothing.music.vizualizer.ui

import com.google.firebase.database.FirebaseDatabase

object FirebaseFuckery {
    fun init() {
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {}
    }
}
