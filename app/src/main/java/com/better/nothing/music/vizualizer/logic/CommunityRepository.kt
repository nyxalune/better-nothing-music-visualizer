package com.better.nothing.music.vizualizer.logic

import android.util.Log
import com.better.nothing.music.vizualizer.model.CommunityPreset
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommunityRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app").getReference("community_presets")

    fun getPresets(): Flow<List<CommunityPreset>> = callbackFlow {
        Log.d("CommunityRepo", "Starting getPresets flow")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("CommunityRepo", "onDataChange: ${snapshot.childrenCount} items")
                val presets = snapshot.children.mapNotNull { 
                    try {
                        it.getValue(CommunityPreset::class.java)?.copy(id = it.key ?: "") 
                    } catch (e: Exception) {
                        Log.e("CommunityRepo", "Error parsing preset: ${it.key}", e)
                        null
                    }
                }
                trySend(presets.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommunityRepo", "onCancelled: ${error.message}")
                close(error.toException())
            }
        }
        database.addValueEventListener(listener)
        awaitClose { 
            Log.d("CommunityRepo", "Closing getPresets flow")
            database.removeEventListener(listener) 
        }
    }

    suspend fun uploadPreset(preset: CommunityPreset) {
        try {
            val key = database.push().key ?: throw Exception("Could not generate key")
            Log.d("CommunityRepo", "Uploading preset with key: $key")
            database.child(key).setValue(preset.copy(id = key)).await()
            Log.d("CommunityRepo", "Upload successful")
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Upload failed", e)
            throw e
        }
    }
    
    suspend fun incrementDownloadCount(presetId: String) {
        val ref = database.child(presetId).child("downloads")
        val snapshot = ref.get().await()
        val current = snapshot.getValue(Int::class.java) ?: 0
        ref.setValue(current + 1).await()
    }
}
