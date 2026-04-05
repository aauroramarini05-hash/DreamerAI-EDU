package com.dreamerai.edu.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "study_memory"
private val Context.dataStore by preferencesDataStore(name = STORE_NAME)

class StudyMemoryStore(private val context: Context) {
    private companion object {
        val LAST_INPUT_KEY = stringPreferencesKey("last_input")
        val LAST_SCORE_KEY = intPreferencesKey("last_score")
        val LAST_STRENGTHS_KEY = stringPreferencesKey("last_strengths")
        val LAST_WEAKNESSES_KEY = stringPreferencesKey("last_weaknesses")
    }

    data class StoredProgress(
        val score: Int,
        val strengths: String,
        val weaknesses: String
    )

    val lastInputFlow: Flow<String> = context.dataStore.data.map { preferences: Preferences ->
        preferences[LAST_INPUT_KEY].orEmpty()
    }

    val lastProgressFlow: Flow<StoredProgress?> = context.dataStore.data.map { preferences ->
        val score = preferences[LAST_SCORE_KEY] ?: return@map null
        StoredProgress(
            score = score,
            strengths = preferences[LAST_STRENGTHS_KEY].orEmpty(),
            weaknesses = preferences[LAST_WEAKNESSES_KEY].orEmpty()
        )
    }

    suspend fun saveLastInput(text: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_INPUT_KEY] = text
        }
    }

    suspend fun saveProgress(score: Int, strengths: String, weaknesses: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SCORE_KEY] = score
            preferences[LAST_STRENGTHS_KEY] = strengths
            preferences[LAST_WEAKNESSES_KEY] = weaknesses
        }
    }
}
