package com.dreamerai.edu.viewmodel

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamerai.edu.data.local.StudyMemoryStore
import com.dreamerai.edu.data.remote.NetworkModule
import com.dreamerai.edu.model.StudyResult
import com.dreamerai.edu.repository.StudyRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ProgressEvaluation(
    val score: Int = 0,
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList()
)

enum class StudySessionStep(val position: Int) {
    INPUT(1),
    EXPLANATION(2),
    QUESTIONS(3),
    INTERROGATION(4),
    FINAL_RESULT(5)
}

data class StudyUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val result: StudyResult? = null,
    val errorMessage: String? = null,
    val clipboardSuggestion: String? = null,
    val rememberedInput: String = "",
    val isOcrLoading: Boolean = false,
    val interrogationAnswers: List<String> = emptyList(),
    val interrogationFeedback: List<String> = emptyList(),
    val interrogationTeacherReplies: List<String> = emptyList(),
    val interrogationFollowUps: List<String> = emptyList(),
    val currentProgress: ProgressEvaluation? = null,
    val lastSavedProgress: ProgressEvaluation? = null,
    val currentStep: StudySessionStep = StudySessionStep.INPUT
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryStore = StudyMemoryStore(application)
    private val repository = StudyRepository(NetworkModule.aiApiService)

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        observeMemory()
        monitorClipboard()
    }

    private fun observeMemory() {
        viewModelScope.launch {
            memoryStore.lastInputFlow.collect { savedInput ->
                _uiState.update { state ->
                    state.copy(rememberedInput = savedInput)
                }
            }
        }

        viewModelScope.launch {
            memoryStore.lastProgressFlow.collect { storedProgress ->
                _uiState.update { state ->
                    if (storedProgress == null) return@update state
                    state.copy(
                        lastSavedProgress = ProgressEvaluation(
                            score = storedProgress.score,
                            strengths = storedProgress.strengths.split("|").filter { it.isNotBlank() },
                            weaknesses = storedProgress.weaknesses.split("|").filter { it.isNotBlank() }
                        )
                    )
                }
            }
        }
    }

    private fun monitorClipboard() {
        val clipboardManager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            val hasText = clipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            val copiedText = if (hasText && clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(getApplication()).toString()
            } else {
                ""
            }

            if (copiedText.isNotBlank()) {
                _uiState.update { state ->
                    state.copy(clipboardSuggestion = "Copied text detected. Tap to study it.")
                }
            }
        }
    }

    fun applyClipboardText() {
        val clipboardManager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val copiedText = clip.getItemAt(0).coerceToText(getApplication()).toString()
            onInputChanged(copiedText)
            _uiState.update { state -> state.copy(clipboardSuggestion = null) }
        }
    }

    fun loadRememberedInput() {
        val remembered = _uiState.value.rememberedInput
        if (remembered.isNotBlank()) {
            onInputChanged(remembered)
        }
    }

    fun onInputChanged(newText: String) {
        _uiState.update { state ->
            state.copy(inputText = newText, errorMessage = null)
        }
    }

    fun nextStep() {
        _uiState.update { state ->
            val next = when (state.currentStep) {
                StudySessionStep.INPUT -> if (state.result != null) StudySessionStep.EXPLANATION else StudySessionStep.INPUT
                StudySessionStep.EXPLANATION -> StudySessionStep.QUESTIONS
                StudySessionStep.QUESTIONS -> StudySessionStep.INTERROGATION
                StudySessionStep.INTERROGATION -> StudySessionStep.FINAL_RESULT
                StudySessionStep.FINAL_RESULT -> StudySessionStep.FINAL_RESULT
            }
            state.copy(currentStep = next)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val previous = when (state.currentStep) {
                StudySessionStep.INPUT -> StudySessionStep.INPUT
                StudySessionStep.EXPLANATION -> StudySessionStep.INPUT
                StudySessionStep.QUESTIONS -> StudySessionStep.EXPLANATION
                StudySessionStep.INTERROGATION -> StudySessionStep.QUESTIONS
                StudySessionStep.FINAL_RESULT -> StudySessionStep.INTERROGATION
            }
            state.copy(currentStep = previous)
        }
    }

    fun goToStep(step: StudySessionStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun onInterrogationAnswerChanged(index: Int, answer: String) {
        _uiState.update { state ->
            if (index !in state.interrogationAnswers.indices) {
                return@update state
            }

            val updatedAnswers = state.interrogationAnswers.toMutableList()
            val updatedFeedback = state.interrogationFeedback.toMutableList()
            val updatedReplies = state.interrogationTeacherReplies.toMutableList()
            val updatedFollowUps = state.interrogationFollowUps.toMutableList()
            updatedAnswers[index] = answer
            updatedFeedback[index] = ""
            updatedReplies[index] = ""
            updatedFollowUps[index] = ""

            state.copy(
                interrogationAnswers = updatedAnswers,
                interrogationFeedback = updatedFeedback,
                interrogationTeacherReplies = updatedReplies,
                interrogationFollowUps = updatedFollowUps
            )
        }
    }

    fun evaluateInterrogationAnswer(index: Int) {
        _uiState.update { state ->
            if (index !in state.interrogationAnswers.indices || state.result == null) {
                return@update state
            }

            val answer = state.interrogationAnswers[index].trim()
            val question = state.result.questions.getOrElse(index) { "" }
            val keywords = extractKeywords(state.result.simplifiedExplanation + " " + question)
            val hasKeywordMatch = keywords.any { keyword ->
                answer.contains(keyword, ignoreCase = true)
            }

            val feedback = when {
                answer.length < 20 -> {
                    "I need a bit more from you. This is too short—try again with a fuller explanation."
                }

                hasKeywordMatch -> {
                    val missingKeyword = keywords.firstOrNull { !answer.contains(it, ignoreCase = true) }
                    if (missingKeyword != null) {
                        "Nice start. Add '$missingKeyword' to make your answer stronger."
                    } else {
                        "Good work—this is clear and mostly complete."
                    }
                }

                else -> {
                    "You're close, but tighten the key concepts and try once more."
                }
            }

            val teacherResponse = generateTeacherSimulation(
                answer = answer,
                hasKeywordMatch = hasKeywordMatch,
                keywords = keywords
            )

            val updatedFeedback = state.interrogationFeedback.toMutableList()
            val updatedReplies = state.interrogationTeacherReplies.toMutableList()
            val updatedFollowUps = state.interrogationFollowUps.toMutableList()
            updatedFeedback[index] = feedback
            updatedReplies[index] = teacherResponse.reply
            updatedFollowUps[index] = teacherResponse.followUpQuestion
            state.copy(
                interrogationFeedback = updatedFeedback,
                interrogationTeacherReplies = updatedReplies,
                interrogationFollowUps = updatedFollowUps,
                currentProgress = calculateProgress(
                    answers = state.interrogationAnswers,
                    result = state.result
                )
            )
        }

        saveProgressSnapshot()
    }

    private data class TeacherSimulationResponse(
        val reply: String,
        val followUpQuestion: String
    )

    private fun generateTeacherSimulation(
        answer: String,
        hasKeywordMatch: Boolean,
        keywords: List<String>
    ): TeacherSimulationResponse {
        val wordCount = answer.split(Regex("\\s+")).count { it.isNotBlank() }
        val mainKeyword = keywords.firstOrNull() ?: "the key concept"

        return when {
            wordCount < 8 -> TeacherSimulationResponse(
                reply = "You're close, try to be more precise. Build your explanation in 2-3 clear points.",
                followUpQuestion = "Can you restate it and include why '$mainKeyword' matters?"
            )

            !hasKeywordMatch -> TeacherSimulationResponse(
                reply = "Good effort. I can see your idea, but you missed core terminology.",
                followUpQuestion = "Now give me an example that uses '$mainKeyword' correctly."
            )

            wordCount in 8..20 -> TeacherSimulationResponse(
                reply = "Good, but can you explain why? Your answer is on track.",
                followUpQuestion = "What would happen if '$mainKeyword' was misunderstood in practice?"
            )

            else -> TeacherSimulationResponse(
                reply = "Excellent structure. You explained it clearly like a prepared student.",
                followUpQuestion = "Great. Now teach it back to me in one real-world scenario."
            )
        }
    }

    private fun extractKeywords(source: String): List<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "that", "this", "from", "into", "your", "have",
            "what", "when", "where", "how", "would", "should", "about", "main", "idea", "real",
            "life", "best", "represents", "explain", "beginner", "topic", "terms", "answer"
        )

        return source
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { token -> token.length >= 4 && token !in stopWords }
            .distinct()
            .take(8)
    }

    private fun calculateProgress(answers: List<String>, result: StudyResult): ProgressEvaluation {
        if (answers.isEmpty()) {
            return ProgressEvaluation()
        }

        var totalLength = 0f
        var totalKeyword = 0f
        var totalCompleteness = 0f

        answers.forEachIndexed { index, answerRaw ->
            val answer = answerRaw.trim()
            val question = result.questions.getOrElse(index) { "" }
            val keywords = extractKeywords(result.simplifiedExplanation + " " + question)
            val keywordMatches = keywords.count { answer.contains(it, ignoreCase = true) }

            val lengthScore = (answer.length.coerceAtMost(120) / 120f) * 40f
            val keywordScore = if (keywords.isEmpty()) 0f else (keywordMatches / keywords.size.toFloat()) * 40f
            val wordCount = answer.split(Regex("\\s+")).count { it.isNotBlank() }
            val completenessScore = when {
                wordCount >= 20 -> 20f
                wordCount >= 12 -> 14f
                wordCount >= 6 -> 8f
                else -> 2f
            }

            totalLength += lengthScore
            totalKeyword += keywordScore
            totalCompleteness += completenessScore
        }

        val count = answers.size.toFloat()
        val avgLength = totalLength / count
        val avgKeyword = totalKeyword / count
        val avgCompleteness = totalCompleteness / count
        val finalScore = (avgLength + avgKeyword + avgCompleteness).roundToInt().coerceIn(0, 100)

        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()

        if (avgLength >= 24f) strengths += "You provide detailed answers with good length."
        else weaknesses += "Expand your answers with more detail and examples."

        if (avgKeyword >= 24f) strengths += "You reference core keywords from the topic."
        else weaknesses += "Mention more core terms from the explanation and questions."

        if (avgCompleteness >= 12f) strengths += "Your responses are mostly complete and structured."
        else weaknesses += "Aim for complete responses (intro, concept, and example)."

        if (strengths.isEmpty()) strengths += "You attempted every question, keep practicing."
        if (weaknesses.isEmpty()) weaknesses += "Great work. Keep consistency across all questions."

        return ProgressEvaluation(
            score = finalScore,
            strengths = strengths,
            weaknesses = weaknesses
        )
    }

    private fun saveProgressSnapshot() {
        viewModelScope.launch {
            val progress = uiState.value.currentProgress ?: return@launch
            memoryStore.saveProgress(
                score = progress.score,
                strengths = progress.strengths.joinToString("|"),
                weaknesses = progress.weaknesses.joinToString("|")
            )
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        viewModelScope.launch {
            runOcr(InputImage.fromBitmap(bitmap, 0))
        }
    }

    fun processGalleryImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val image = InputImage.fromFilePath(getApplication(), uri)
                runOcr(image)
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(errorMessage = exception.message ?: "Unable to open image from gallery.")
                }
            }
        }
    }

    private suspend fun runOcr(inputImage: InputImage) {
        _uiState.update { it.copy(isOcrLoading = true, errorMessage = null) }
        try {
            val visionText = textRecognizer.process(inputImage).await()
            val extractedText = visionText.text.trim()
            if (extractedText.isBlank()) {
                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        errorMessage = "No text found in image. Try another photo."
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    inputText = extractedText,
                    isOcrLoading = false,
                    clipboardSuggestion = null,
                    errorMessage = null
                )
            }
            study()
        } catch (exception: Exception) {
            _uiState.update {
                it.copy(
                    isOcrLoading = false,
                    errorMessage = exception.message ?: "OCR failed. Please try again."
                )
            }
        }
    }

    fun study() {
        val input = uiState.value.inputText.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please paste study content first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                memoryStore.saveLastInput(input)
                val result = repository.generateStudyContent(input)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        result = result,
                        interrogationAnswers = List(result.questions.size) { "" },
                        interrogationFeedback = List(result.questions.size) { "" },
                        interrogationTeacherReplies = List(result.questions.size) { "" },
                        interrogationFollowUps = List(result.questions.size) { "" },
                        currentProgress = ProgressEvaluation(),
                        currentStep = StudySessionStep.EXPLANATION
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Unable to generate study content."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}
