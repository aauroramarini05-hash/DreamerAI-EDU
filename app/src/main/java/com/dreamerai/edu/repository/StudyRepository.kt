package com.dreamerai.edu.repository

import com.dreamerai.edu.data.remote.AiApiService
import com.dreamerai.edu.model.StudyResult
import kotlinx.coroutines.delay

class StudyRepository(private val apiService: AiApiService? = null) {

    suspend fun generateStudyContent(text: String): StudyResult {
        // Placeholder for future API integration using Retrofit service.
        // Example API usage can be enabled once backend is available.
        // val response = apiService?.generateStudy(StudyRequest(text))

        delay(400)
        val cleanText = text.trim().replace("\n", " ")
        val core = cleanText.take(300)

        return StudyResult(
            simplifiedExplanation =
                "In simple terms: ${core.ifBlank { "No content provided yet." }}. " +
                    "Focus on key ideas, definitions, and one practical example.",
            questions = listOf(
                "1) What is the main idea of this topic?",
                "2) How would you explain it to a beginner?",
                "3) What real-life example best represents it?"
            ),
            oralSimulation =
                "Oral simulation: Imagine I am your teacher. Summarize the topic in 60 seconds, " +
                    "define two important terms, and answer one follow-up question confidently."
        )
    }
}
