package com.dreamerai.edu.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class StudyRequest(val text: String)

data class StudyApiResponse(
    val simplifiedExplanation: String,
    val questions: List<String>,
    val oralSimulation: String
)

interface AiApiService {
    @POST("study/generate")
    suspend fun generateStudy(@Body request: StudyRequest): StudyApiResponse
}
