package com.agent008.writer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Screen { HOME, PROJECTS, EDITOR, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(this)
        setContent { Agent008App(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Agent008App(store: LocalStore) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var projects by remember { mutableStateOf(store.projects()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = projects.firstOrNull { it.id == selectedId }
    var projectTitle by remember(selected?.id) { mutableStateOf(selected?.title.orEmpty()) }
    var projectText by remember(selected?.id) { mutableStateOf(selected?.text.orEmpty()) }
    var apiKey by remember { mutableStateOf(store.apiKey()) }
    var prompt by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf(listOf("assistant" to "Привет! Я Agent008 — твой литературный помощник. Попроси меня придумать сюжет, написать сцену или отредактировать текст.")) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val client = remember { DeepSeekClient() }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFB7C7FF),
        secondary = androidx.compose.ui.graphics.Color(0xFFB8CBBE),
        background = androidx.compose.ui.graphics.Color(0xFF101318),
        surface = androidx.compose.ui.graphics.Color(0xFF191D24)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column { Text("Agent008", fontWeight = FontWeight.Bold); Text("Литературная мастерская", style = MaterialTheme.typography.labelSmall) } },
                    actions = { IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, "Настройки") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen == Screen.HOME, onClick = { screen = Screen.HOME }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("Агент") })
                    NavigationBarItem(selected = screen == Screen.PROJECTS || screen == Screen.EDITOR, onClick = { screen = Screen.PROJECTS }, icon = { Icon(Icons.Default.Book, null) }, label = { Text("Книги") })
                    NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                status?.let { message ->
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { status = null }) { Icon(Icons.Default.Close, "Закрыть") }
                        }
                    }
                }
                when (screen) {
                    Screen.HOME -> {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Твоя творческая мастерская", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Пиши произведения, развивай персонажей, редактируй главы и сохраняй рабочие заметки.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            val p = WriterProject(UUID.randomUUID().toString(), "Новая книга", "")
                                            store.saveProject(p); projects = store.projects(); selectedId = p.id
                                            projectTitle = p.title; projectText = ""; screen = Screen.EDITOR
                                        }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Новая книга") }
                                        OutlinedButton(onClick = { screen = Screen.PROJECTS }) { Text("Мои проекты") }
                                    }
                                }
                            }
                            chat.forEach { (role, content) ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(if (role == "user") "Ты" else "Agent008", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp)); Text(content)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f), label = { Text("Задание для агента") }, minLines = 1, maxLines = 4)
                            Spacer(Modifier.width(8.dp))
                            IconButton(enabled = !busy && prompt.isNotBlank(), onClick = {
                                val userText = prompt.trim(); prompt = ""
                                chat = chat + ("user" to userText); busy = true
                                scope.launch {
                                    try {
                                        val context = selected?.text?.takeIf { it.isNotBlank() }?.let { "\n\nТекст активного проекта:\n" + it.takeLast(12000) }.orEmpty()
                                        val system = "Ты Agent008, личный литературный помощник писателя. Отвечай по-русски, если пользователь не просит иначе. Пиши прозу, анализируй сюжет, редактируй стиль и объясняй правки. Не выдумывай факты канона: отмечай предположения."
                                        val messages = listOf("system" to system) + chat.takeLast(12) + listOf("user" to (userText + context))
                                        val answer = client.complete(store.apiKey(), messages)
                                        chat = chat + ("assistant" to answer)
                                    } catch (e: Exception) {
                                        status = e.message ?: "Не удалось связаться с DeepSeek."
                                        chat = chat + ("assistant" to "Не получилось выполнить запрос. Проверь интернет и API-ключ в настройках.")
                                    } finally { busy = false }
                                }
                            }) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, "Отправить") }
                        }
                    }
                    Screen.PROJECTS -> {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Мои книги", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val p = WriterProject(UUID.randomUUID().toString(), "Новая книга", "")
                                store.saveProject(p); projects = store.projects(); selectedId = p.id
                                projectTitle = p.title; projectText = ""; screen = Screen.EDITOR
                            }) { Icon(Icons.Default.Add, null); Text("Создать") }
                        }
                        if (projects.isEmpty()) Text("Пока нет проектов. Создай первую книгу.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(projects, key = { it.id }) { p ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    selectedId = p.id; projectTitle = p.title; projectText = p.text; screen = Screen.EDITOR
                                }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.title, fontWeight = FontWeight.SemiBold)
                                            Text(p.text.take(90).ifBlank { "Пустая рукопись" }, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                        }
                    }
                    Screen.EDITOR -> {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Редактор рукописи", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { screen = Screen.PROJECTS }) { Text("Назад") }
                        }
                        OutlinedTextField(value = projectTitle, onValueChange = { projectTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название произведения") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = projectText, onValueChange = { projectText = it }, modifier = Modifier.weight(1f).fillMaxWidth(), label = { Text("Текст рукописи") }, placeholder = { Text("Пиши здесь или вставь главу…") })
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val p = WriterProject(selectedId ?: UUID.randomUUID().toString(), projectTitle.ifBlank { "Без названия" }, projectText)
                                store.saveProject(p); projects = store.projects(); selectedId = p.id; status = "Рукопись сохранена на устройстве."
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("Сохранить") }
                            OutlinedButton(onClick = {
                                prompt = "Отредактируй этот текст. Сначала перечисли важные проблемы, затем предложи улучшенную версию, сохранив авторский голос:\n\n" + projectText.take(10000)
                                screen = Screen.HOME
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text("Редактор ИИ") }
                        }
                    }
                    Screen.SETTINGS -> {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Настройки DeepSeek", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("API-ключ хранится в зашифрованных настройках приложения. Не публикуй его и не добавляй в Git.")
                            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("DeepSeek API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            Button(onClick = { store.saveApiKey(apiKey); status = "Ключ сохранён на устройстве." }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить ключ") }
                            OutlinedButton(onClick = { store.removeApiKey(); apiKey = ""; status = "Ключ удалён." }, modifier = Modifier.fillMaxWidth()) { Text("Удалить ключ") }
                            HorizontalDivider()
                            Text("Модель: deepseek-chat", fontWeight = FontWeight.SemiBold)
                            Text("Запросы отправляются на api.deepseek.com. Локальные проекты не синхронизируются автоматически.")
                            Text("Версия 0.1.0 · Agent008")
                        }
                    }
                }
            }
        }
    }
}
