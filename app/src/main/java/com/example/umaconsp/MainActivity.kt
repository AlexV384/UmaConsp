package com.example.umaconsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.umaconsp.data.localstorage.PrivateFolder
import com.example.umaconsp.llamacpp.Native
import com.example.umaconsp.presentation.chat.ChatScreen
import com.example.umaconsp.presentation.chatlist.ChatListScreen
import com.example.umaconsp.presentation.chatlist.ChatListViewModel
import com.example.umaconsp.presentation.settings.SettingsDrawerContent
import com.example.umaconsp.presentation.settings.SettingsManager
import com.example.umaconsp.presentation.theme.ThemeManager
import com.example.umaconsp.ui.theme.UmaconspTheme
import com.example.umaconsp.utils.AiApi
import com.example.umaconsp.utils.LocalChatListViewModel
import kotlinx.coroutines.launch

/**
 * Главная активность приложения.
 * Отвечает за:
 * - Инициализацию менеджеров тем и настроек.
 * - Установку Compose-содержимого.
 * - Навигацию между экранами (список чатов → чат).
 * - Боковое меню (Drawer) с настройками.
 * - Обработку системной кнопки "Назад" для закрытия меню.
 * - Синхронизацию IP‑адреса сервера из настроек.
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Менеджеры для хранения настроек (тема и IP сервера)
        val themeManager = ThemeManager(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val modelManager = PrivateFolder(applicationContext)
        val localModelLoader = Native()

        // Общая ViewModel для списка чатов (живёт на уровне активности)
        val chatListViewModel = ChatListViewModel()

        setContent {
            // Подписываемся на изменения темы (тёмная/светлая)
            val isDarkTheme by themeManager.isDarkTheme.collectAsState(initial = false)

            // Подписываемся на изменения IP-адреса сервера
            val serverIp by settingsManager.serverIpFlow.collectAsState(initial = SettingsManager.DEFAULT_IP)

            // Состояние для списка моделей
            var modelList by remember { mutableStateOf(modelManager.getImportedModels()) }

            // При изменении IP обновляем глобальную настройку в объекте AiApi
            LaunchedEffect(serverIp) {
                AiApi.currentIp = serverIp
            }

            // Применяем тему (кастомная обёртка над MaterialTheme)
            UmaconspTheme(darkTheme = isDarkTheme) {
                // Пробрасываем ChatListViewModel через CompositionLocal для доступа из любого компонента
                CompositionLocalProvider(LocalChatListViewModel provides chatListViewModel) {
                    // Состояние бокового меню (открыто/закрыто)
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val navController = rememberNavController()

                    // Обработка кнопки "Назад" — если меню открыто, закрываем его
                    BackHandler(enabled = drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    }

                    // Следим за удалением чата (событие из ChatListViewModel)
                    LaunchedEffect(Unit) {
                        chatListViewModel.chatDeleted.collect { deletedChatId ->
                            // Если текущий открытый чат совпадает с удалённым — возвращаемся к списку
                            val currentEntry = navController.currentBackStackEntry
                            if (currentEntry?.destination?.route == "chat/{chatId}" &&
                                currentEntry.arguments?.getString("chatId") == deletedChatId
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
                                        localModelLoader.unloadModelPub()
                                    } else {
                                        val fullPath = applicationContext.filesDir.path + "/" + name
                                        localModelLoader.loadModelPub(fullPath)
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
                                startDestination = "chat_list"
                            ) {
                                // Экран списка чатов
                                composable("chat_list") {
                                    ChatListScreen(
                                        viewModel = chatListViewModel,
                                        onChatClick = { chatId ->
                                            navController.navigate("chat/$chatId")
                                        },
                                        onCreateChat = {
                                            scope.launch {
                                                val newId = chatListViewModel.createNewChat()
                                                navController.navigate("chat/$newId")
                                            }
                                        },
                                        onOpenSettings = {
                                            scope.launch { drawerState.open() }
                                        }
                                    )
                                }

                                // Экран конкретного чата (параметр chatId в маршруте)
                                composable("chat/{chatId}") { backStackEntry ->
                                    val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                                    ChatScreen(
                                        chatId = chatId,
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
