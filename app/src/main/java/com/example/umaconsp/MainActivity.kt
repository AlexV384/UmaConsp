package com.example.umaconsp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.umaconsp.ai.LocalAiProvider as LocalAiProviderLocal
import com.example.umaconsp.ai.PlaintextResponseParser
import com.example.umaconsp.ai.TesseractAiProvider
import com.example.umaconsp.data.localstorage.PrivateFolder
import com.example.umaconsp.llamacpp.Native
import com.example.umaconsp.presentation.debug.ChatDebugActivity
import com.example.umaconsp.presentation.document.DocumentScreen
import com.example.umaconsp.presentation.documentlist.DocumentListScreen
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel
import com.example.umaconsp.presentation.onboarding.OnboardingScreen
import com.example.umaconsp.presentation.settings.SettingsDrawerContent
import com.example.umaconsp.presentation.settings.SettingsManager
import com.example.umaconsp.presentation.theme.ThemeManager
import com.example.umaconsp.ui.theme.UmaconspTheme
import com.example.umaconsp.utils.LocalAiProvider as LocalAiProviderState
import com.example.umaconsp.utils.LocalDocumentListViewModel as LocalDocumentListViewModelState
import com.example.umaconsp.utils.LocalResponseParser as LocalResponseParserState
import kotlinx.coroutines.launch

private const val MODE_TESSERACT = "tesseract"
private const val MODE_LOCAL = "local_model"

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themeManager = ThemeManager(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val documentListViewModel = DocumentListViewModel()
        val modelManager = PrivateFolder(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()

            val isDarkTheme by themeManager.isDarkTheme.collectAsState(initial = false)
            val modelMode by settingsManager.modelModeFlow.collectAsState(initial = MODE_TESSERACT)
            val ocrLanguage by settingsManager.ocrLanguageFlow.collectAsState(
                initial = SettingsManager.DEFAULT_OCR_LANGUAGE
            )
            val exportFolderUri by settingsManager.exportFolderUriFlow.collectAsState(initial = null)

            var isFirstLaunch by remember { mutableStateOf<Boolean?>(null) }
            var modelList by remember { mutableStateOf(modelManager.getImportedModels()) }

            LaunchedEffect(Unit) {
                settingsManager.isFirstLaunch.collect { value ->
                    isFirstLaunch = value
                }
            }

            val (aiProvider, responseParser) = remember(modelMode, ocrLanguage) {
                when (modelMode) {
                    MODE_LOCAL -> LocalAiProviderLocal() to PlaintextResponseParser()
                    MODE_TESSERACT -> TesseractAiProvider(ocrLanguage) to PlaintextResponseParser()
                    else -> TesseractAiProvider(ocrLanguage) to PlaintextResponseParser()
                }
            }

            UmaconspTheme(darkTheme = isDarkTheme) {
                when (isFirstLaunch) {
                    null -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    true -> {
                        OnboardingScreen(
                            onCompleted = {
                                isFirstLaunch = false
                                scope.launch {
                                    settingsManager.setFirstLaunchCompleted()
                                }
                            },
                            onExportFolderPicked = { uri ->
                                val takeFlags =
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                                contentResolver.takePersistableUriPermission(uri, takeFlags)
                                settingsManager.setExportFolderUri(uri.toString())
                            },
                            onExportFolderSet = { },
                            onModelModeChange = { mode ->
                                scope.launch {
                                    settingsManager.setModelMode(mode)
                                }
                            },
                            onOcrLanguageChange = { language ->
                                scope.launch {
                                    settingsManager.setOcrLanguage(language)
                                }
                            },
                            onModelDirPicked = { uri ->
                                modelManager.importModel(uri)
                                modelList = modelManager.getImportedModels()
                            },
                            modelList = modelList,
                            onLocalModelPicked = { name ->
                                if (name == "(unload)") {
                                    Native.unloadModelPub()
                                } else {
                                    val fullPath = applicationContext.filesDir.path + "/" + name
                                    Native.loadModelPub(fullPath)
                                }
                            }
                        )
                    }

                    false -> {
                        CompositionLocalProvider(
                            LocalDocumentListViewModelState provides documentListViewModel,
                            LocalAiProviderState provides aiProvider,
                            LocalResponseParserState provides responseParser
                        ) {
                            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                            val navController = rememberNavController()

                            BackHandler(enabled = drawerState.isOpen) {
                                scope.launch { drawerState.close() }
                            }

                            LaunchedEffect(Unit) {
                                documentListViewModel.documentDeleted.collect { deletedId ->
                                    val currentEntry = navController.currentBackStackEntry
                                    if (
                                        currentEntry?.destination?.route == "document/{documentId}" &&
                                        currentEntry.arguments?.getString("documentId") == deletedId
                                    ) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            ModalNavigationDrawer(
                                drawerState = drawerState,
                                drawerContent = {
                                    SettingsDrawerContent(
                                        exportFolderUri = exportFolderUri,
                                        onExportFolderPicked = { uri ->
                                            scope.launch {
                                                val takeFlags =
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                                                contentResolver.takePersistableUriPermission(uri, takeFlags)
                                                settingsManager.setExportFolderUri(uri.toString())
                                            }
                                        },
                                        isDarkTheme = isDarkTheme,
                                        onThemeChange = { enabled ->
                                            scope.launch { themeManager.setDarkTheme(enabled) }
                                        },
                                        modelMode = modelMode,
                                        onModelModeChange = { mode ->
                                            scope.launch { settingsManager.setModelMode(mode) }
                                        },
                                        ocrLanguage = ocrLanguage,
                                        onOcrLanguageChange = { language ->
                                            scope.launch { settingsManager.setOcrLanguage(language) }
                                        },
                                        onModelDirPicked = { uri ->
                                            modelManager.importModel(uri)
                                            modelList = modelManager.getImportedModels()
                                        },
                                        modelList = modelList,
                                        onLocalModelPicked = { name ->
                                            if (name == "(unload)") {
                                                Native.unloadModelPub()
                                            } else {
                                                val fullPath = applicationContext.filesDir.path + "/" + name
                                                Native.loadModelPub(fullPath)
                                            }
                                        }
                                    )
                                }
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = "document_list"
                                    ) {
                                        composable("document_list") {
                                            DocumentListScreen(
                                                viewModel = documentListViewModel,
                                                onDocumentClick = { docId ->
                                                    navController.navigate("document/$docId")
                                                },
                                                onCreateDocument = {
                                                    scope.launch {
                                                        val newId = documentListViewModel.createNewDocument()
                                                        navController.navigate("document/$newId")
                                                    }
                                                },
                                                onOpenSettings = {
                                                    scope.launch { drawerState.open() }
                                                },
                                                onOpenChat = {
                                                    val intent = Intent(
                                                        this@MainActivity,
                                                        ChatDebugActivity::class.java
                                                    )
                                                    startActivity(intent)
                                                }
                                            )
                                        }

                                        composable("document/{documentId}") { backStackEntry ->
                                            val documentId =
                                                backStackEntry.arguments?.getString("documentId")
                                                    ?: return@composable

                                            DocumentScreen(
                                                documentId = documentId,
                                                onBack = { navController.popBackStack() },
                                                onOpenSettings = {
                                                    scope.launch { drawerState.open() }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
