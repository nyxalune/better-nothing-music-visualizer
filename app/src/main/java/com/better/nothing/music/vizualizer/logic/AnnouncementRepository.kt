package com.better.nothing.music.vizualizer.logic

import android.util.Log
import com.better.nothing.music.vizualizer.model.Announcement
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AnnouncementRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("announcements")

    fun getLatestAnnouncement(): Flow<Announcement?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val announcement = snapshot.child("latest").getValue(Announcement::class.java)
                trySend(announcement)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AnnouncementRepo", "onCancelled: ${error.message}")
                close(error.toException())
            }
        }
        database.addValueEventListener(listener)
        awaitClose { database.removeEventListener(listener) }
    }

    fun getAnnouncementHistory(): Flow<List<Announcement>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val history = snapshot.child("history").children.mapNotNull {
                    it.getValue(Announcement::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(history)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        database.addValueEventListener(listener)
        awaitClose { database.removeEventListener(listener) }
    }

    suspend fun postAnnouncement(announcement: Announcement) {
        try {
            database.child("latest").setValue(announcement).await()
            // Also store in history
            database.child("history").child(announcement.id).setValue(announcement).await()
        } catch (e: Exception) {
            Log.e("AnnouncementRepo", "Post announcement failed", e)
            throw e
        }
    }
}
