package com.example.umaconsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.umaconsp.data.localstorage.PrivateFolder
import com.example.umaconsp.llamacpp.Native
import com.example.umaconsp.presentation.document.DocumentScreen
import com.example.umaconsp.presentation.documentlist.DocumentListScreen
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel
import com.example.umaconsp.presentation.settings.SettingsDrawerContent
import com.example.umaconsp.presentation.settings.SettingsManager
import com.example.umaconsp.presentation.theme.ThemeManager
import com.example.umaconsp.ui.theme.UmaconspTheme
import com.example.umaconsp.utils.AiApi
import com.example.umaconsp.utils.LocalDocumentListViewModel
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themeManager = ThemeManager(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val documentListViewModel = DocumentListViewModel()
        val modelManager = PrivateFolder(applicationContext)

        val chatListViewModel = DocumentListViewModel()

        setContent {
            val isDarkTheme by themeManager.isDarkTheme.collectAsState(initial = false)
            val serverIp by settingsManager.serverIpFlow.collectAsState(initial = SettingsManager.DEFAULT_IP)
            var modelList by remember { mutableStateOf(modelManager.getImportedModels()) }

            LaunchedEffect(serverIp) {
                AiApi.currentIp = serverIp
            }

            // Применяем тему (кастомная обёртка над MaterialTheme)
            UmaconspTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalDocumentListViewModel provides documentListViewModel) {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val navController = rememberNavController()

                    // Обработка кнопки "Назад" — если меню открыто, закрываем его
                    BackHandler(enabled = drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    }

                    LaunchedEffect(Unit) {
                        documentListViewModel.documentDeleted.collect { deletedId ->
                            val currentEntry = navController.currentBackStackEntry
                            if (currentEntry?.destination?.route == "document/{documentId}" &&
                                currentEntry.arguments?.getString("documentId") == deletedId
                            ) {
                                navController.popBackStack()
                            }
                        }
                    }

                    // Боковое меню (Drawer) с настройками
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            SettingsDrawerContent(
                                isDarkTheme = isDarkTheme,
                                onThemeChange = { enabled ->
                                    scope.launch {
                                        themeManager.setDarkTheme(enabled)
                                    }
                                },
                                serverIp = serverIp,
                                onIpChange = { newIp ->
                                    scope.launch {
                                        settingsManager.setServerIp(newIp)
                                    }
                                },
                                onModelDirPicked = { uri ->
                                    modelManager.importModel(uri)
                                    modelList = modelManager.getImportedModels()
                                },
                                modelList = modelList,
                                onLocalModelPicked = { name ->
                                    if (name == "(unload)"){
                                        Native.unloadModelPub()
                                    } else {
                                        val fullPath = applicationContext.filesDir.path + "/" + name
                                        Native.loadModelPub(fullPath)
                                    }
                                }
                            )
                        }
                    ) {
                        // Основная поверхность приложения
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            // Навигация между экранами
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
                                        }
                                    )
                                }
                                composable("document/{documentId}") { backStackEntry ->
                                    val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
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
