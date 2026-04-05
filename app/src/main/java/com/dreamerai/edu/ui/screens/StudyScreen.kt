package com.dreamerai.edu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dreamerai.edu.model.StudyResult
import com.dreamerai.edu.ui.theme.SurfaceLight
import com.dreamerai.edu.viewmodel.ProgressEvaluation
import com.dreamerai.edu.viewmodel.StudySessionStep
import com.dreamerai.edu.viewmodel.StudyUiState

@Composable
fun StudyScreen(
    state: StudyUiState,
    onInputChanged: (String) -> Unit,
    onStudyClick: () -> Unit,
    onClipboardSuggestionClick: () -> Unit,
    onLoadRememberedClick: () -> Unit,
    onCapturePhotoClick: () -> Unit,
    onPickGalleryClick: () -> Unit,
    onInterrogationAnswerChanged: (Int, String) -> Unit,
    onEvaluateAnswer: (Int) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onStepSelected: (StudySessionStep) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            Text(
                text = "DreamerAI Edu",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Guided learning session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SessionProgress(state.currentStep)
        }

        item {
            if (state.clipboardSuggestion != null && state.currentStep == StudySessionStep.INPUT) {
                SuggestionCard(
                    text = state.clipboardSuggestion,
                    onClick = onClipboardSuggestionClick
                )
            }
        }

        item {
            if (state.rememberedInput.isNotBlank() && state.currentStep == StudySessionStep.INPUT) {
                SuggestionCard(
                    text = "Load last saved study note",
                    onClick = onLoadRememberedClick
                )
            }
        }

        item {
            when (state.currentStep) {
                StudySessionStep.INPUT -> InputStepCard(
                    state = state,
                    onInputChanged = onInputChanged,
                    onCapturePhotoClick = onCapturePhotoClick,
                    onPickGalleryClick = onPickGalleryClick,
                    onStudyClick = onStudyClick
                )

                StudySessionStep.EXPLANATION -> ExplanationStepCard(state.result)
                StudySessionStep.QUESTIONS -> QuestionsStepCard(state.result)
                StudySessionStep.INTERROGATION -> InterrogationStepCard(
                    result = state.result,
                    answers = state.interrogationAnswers,
                    feedback = state.interrogationFeedback,
                    teacherReplies = state.interrogationTeacherReplies,
                    followUps = state.interrogationFollowUps,
                    onAnswerChanged = onInterrogationAnswerChanged,
                    onEvaluateAnswer = onEvaluateAnswer
                )

                StudySessionStep.FINAL_RESULT -> FinalResultStepCard(
                    result = state.result,
                    currentProgress = state.currentProgress,
                    lastSavedProgress = state.lastSavedProgress
                )
            }
        }

        item {
            if (state.isLoading || state.isOcrLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        item {
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SessionNavigation(
                currentStep = state.currentStep,
                hasResult = state.result != null,
                onNextStep = onNextStep,
                onPreviousStep = onPreviousStep,
                onStepSelected = onStepSelected
            )
        }
    }
}

@Composable
private fun SessionProgress(currentStep: StudySessionStep) {
    val progress = currentStep.position / 5f
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Step ${currentStep.position}/5: ${stepTitle(currentStep)}")
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SessionNavigation(
    currentStep: StudySessionStep,
    hasResult: Boolean,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onStepSelected: (StudySessionStep) -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Session navigation", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPreviousStep,
                    enabled = currentStep != StudySessionStep.INPUT,
                    modifier = Modifier.weight(1f)
                ) { Text("Back") }

                Button(
                    onClick = onNextStep,
                    enabled = hasResult && currentStep != StudySessionStep.FINAL_RESULT,
                    modifier = Modifier.weight(1f)
                ) { Text("Next") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StudySessionStep.values().forEach { step ->
                    TextButton(
                        onClick = { onStepSelected(step) },
                        enabled = hasResult || step == StudySessionStep.INPUT
                    ) {
                        Text(step.position.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun InputStepCard(
    state: StudyUiState,
    onInputChanged: (String) -> Unit,
    onCapturePhotoClick: () -> Unit,
    onPickGalleryClick: () -> Unit,
    onStudyClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Step 1: Input", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputChanged,
                label = { Text("Study content") },
                placeholder = { Text("Paste your lesson text here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCapturePhotoClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture photo")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take photo")
                }

                OutlinedButton(onClick = onPickGalleryClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Collections, contentDescription = "Pick from gallery")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("From gallery")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onStudyClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !state.isOcrLoading
            ) {
                Text("Start guided study")
            }
        }
    }
}

@Composable
private fun ExplanationStepCard(result: StudyResult?) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Step 2: Explanation", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(result?.simplifiedExplanation ?: "Generate a study session to continue.")
        }
    }
}

@Composable
private fun QuestionsStepCard(result: StudyResult?) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Step 3: Questions", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            result?.questions?.forEach { question ->
                Text("• $question")
                Spacer(modifier = Modifier.height(4.dp))
            } ?: Text("No questions yet.")
        }
    }
}

@Composable
private fun InterrogationStepCard(
    result: StudyResult?,
    answers: List<String>,
    feedback: List<String>,
    teacherReplies: List<String>,
    followUps: List<String>,
    onAnswerChanged: (Int, String) -> Unit,
    onEvaluateAnswer: (Int) -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Step 4: Interrogation", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            result?.questions?.forEachIndexed { index, question ->
                Text(text = question)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = answers.getOrElse(index) { "" },
                    onValueChange = { onAnswerChanged(index, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your answer") }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEvaluateAnswer(index) }) {
                        Text("Check answer")
                    }
                }
                val feedbackText = feedback.getOrElse(index) { "" }
                if (feedbackText.isNotBlank()) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val teacherReply = teacherReplies.getOrElse(index) { "" }
                if (teacherReply.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            text = "Teacher: $teacherReply",
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                val followUpQuestion = followUps.getOrElse(index) { "" }
                if (followUpQuestion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Follow-up: $followUpQuestion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            } ?: Text("No questions available.")
        }
    }
}

@Composable
private fun FinalResultStepCard(
    result: StudyResult?,
    currentProgress: ProgressEvaluation?,
    lastSavedProgress: ProgressEvaluation?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (currentProgress ?: lastSavedProgress)?.let { progress ->
            ProgressCard(
                title = "Your understanding level",
                progress = progress,
                isSaved = currentProgress == null
            )
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Step 5: Final result", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(result?.oralSimulation ?: "No oral simulation generated yet.")
            }
        }
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ProgressCard(title: String, progress: ProgressEvaluation, isSaved: Boolean) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Progress: ${progress.score}%")
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.score / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("Strengths", fontWeight = FontWeight.Medium)
            progress.strengths.forEach { item ->
                Text("• $item")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Weaknesses", fontWeight = FontWeight.Medium)
            progress.weaknesses.forEach { item ->
                Text("• $item")
            }

            if (isSaved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Showing last saved evaluation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun stepTitle(step: StudySessionStep): String = when (step) {
    StudySessionStep.INPUT -> "Input"
    StudySessionStep.EXPLANATION -> "Explanation"
    StudySessionStep.QUESTIONS -> "Questions"
    StudySessionStep.INTERROGATION -> "Interrogation"
    StudySessionStep.FINAL_RESULT -> "Final result"
}
