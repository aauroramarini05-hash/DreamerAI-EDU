package com.dreamerai.edu

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dreamerai.edu.ui.screens.StudyScreen
import com.dreamerai.edu.ui.theme.DreamerAIEduTheme
import com.dreamerai.edu.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DreamerAIEduTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                val takePictureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    bitmap?.let { viewModel.processCapturedImage(it) }
                }

                val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        takePictureLauncher.launch(null)
                    }
                }

                val pickImageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    uri?.let { viewModel.processGalleryImage(it) }
                }

                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
                }

                StudyScreen(
                    state = uiState,
                    onInputChanged = viewModel::onInputChanged,
                    onStudyClick = viewModel::study,
                    onClipboardSuggestionClick = viewModel::applyClipboardText,
                    onLoadRememberedClick = viewModel::loadRememberedInput,
                    onCapturePhotoClick = {
                        requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    onPickGalleryClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onInterrogationAnswerChanged = viewModel::onInterrogationAnswerChanged,
                    onEvaluateAnswer = viewModel::evaluateInterrogationAnswer,
                    onNextStep = viewModel::nextStep,
                    onPreviousStep = viewModel::previousStep,
                    onStepSelected = viewModel::goToStep
                )

                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}
