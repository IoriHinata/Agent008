package com.agent008.writer

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var task by remember { mutableStateOf("") }
    var preparedPrompt by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var settingsInfo by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun refreshProjects() { projects = store.projects() }
    fun saveCurrentProject() {
        val id = selectedId ?: UUID.randomUUID().toString()
        val p = WriterProject(id, projectTitle.ifBlank { "Без названия" }, projectText)
        store.saveProject(p)
        selectedId = id
        refreshProjects()
    }
    fun makePrompt(): String {
        val manuscriptContext = projectText.trim().takeLast(14000)
        return buildString {
            append("Ты — мой литературный помощник Agent008. Отвечай по-русски, если не указано иное. ")
            append("Соблюдай канон и детали рукописи, не придумывай факты о мире без пометки. ")
            append("Если задача творческая, сохраняй авторский голос и объясняй существенные изменения.\n\n")
            append("ПРОЕКТ: ").append(projectTitle.ifBlank { "Без названия" }).append("\n")
            append("ЗАДАНИЕ:\n").append(task.trim()).append("\n")
            if (manuscriptContext.isNotBlank()) {
                append("\nКОНТЕКСТ РУКОПИСИ (фрагмент в конце текста):\n")
                append(manuscriptContext).append("\n")
            }
        }
    }
    fun openDeepSeek() {
        val launch = context.packageManager.getLaunchIntentForPackage("com.deepseek.chat")
        try {
            if (launch != null) {
                context.startActivity(launch)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.deepseek.com/")))
            }
        } catch (_: Exception) {
            status = "Не удалось открыть DeepSeek. Установи приложение или открой chat.deepseek.com."
        }
    }
    fun sharePrompt() {
        if (preparedPrompt.isBlank()) {
            status = "Сначала подготовь запрос."
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, preparedPrompt)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Передать запрос в DeepSeek или другое приложение"))
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFB7C7FF),
        secondary = androidx.compose.ui.graphics.Color(0xFFB8CBBE),
        background = androidx.compose.ui.graphics.Color(0xFF101318),
        surface = androidx.compose.ui.graphics.Color(0xFF191D24)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Agent008", fontWeight = FontWeight.Bold)
                            Text("Писательская мастерская · без API", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { screen = Screen.SETTINGS }) {
                            Icon(Icons.Default.Settings, "О приложении")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.HOME,
                        onClick = { screen = Screen.HOME },
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        label = { Text("Мастерская") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.PROJECTS || screen == Screen.EDITOR,
                        onClick = { screen = Screen.PROJECTS },
                        icon = { Icon(Icons.Default.Book, null) },
                        label = { Text("Книги") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.SETTINGS,
                        onClick = { screen = Screen.SETTINGS },
                        icon = { Icon(Icons.Default.Info, null) },
                        label = { Text("О приложении") }
                    )
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
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Твоя творческая мастерская", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Agent008 хранит рукописи на телефоне и готовит запросы. Ответы генерирует установленный DeepSeek — платный API-ключ не нужен.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            val p = WriterProject(UUID.randomUUID().toString(), "Новая книга", "")
                                            store.saveProject(p); refreshProjects(); selectedId = p.id
                                            projectTitle = p.title; projectText = ""; screen = Screen.EDITOR
                                        }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Новая книга") }
                                        OutlinedButton(onClick = { screen = Screen.PROJECTS }) { Text("Мои проекты") }
                                    }
                                }
                            }

                            Text("1. Задание для DeepSeek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (projects.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                    OutlinedTextField(
                                        value = selected?.title ?: "Без активного проекта",
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        label = { Text("Контекст проекта") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                                    )
                                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        projects.forEach { p ->
                                            DropdownMenuItem(text = { Text(p.title) }, onClick = {
                                                selectedId = p.id; projectTitle = p.title; projectText = p.text; expanded = false
                                            })
                                        }
                                        DropdownMenuItem(text = { Text("Без проекта") }, onClick = {
                                            selectedId = null; projectTitle = ""; projectText = ""; expanded = false
                                        })
                                    }
                                }
                            } else {
                                Text("Создай книгу, чтобы добавлять фрагменты рукописи в контекст запроса.", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = task,
                                onValueChange = { task = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Что нужно сделать?") },
                                placeholder = { Text("Например: найди сюжетные дыры в главе или напиши сцену…") },
                                minLines = 3,
                                maxLines = 7
                            )
                            Button(
                                onClick = {
                                    if (task.isBlank()) status = "Сначала напиши задание."
                                    else { preparedPrompt = makePrompt(); status = "Запрос подготовлен. Передай его в DeepSeek." }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Подготовить запрос") }

                            if (preparedPrompt.isNotBlank()) {
                                Text("2. Передай запрос в DeepSeek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = preparedPrompt,
                                    onValueChange = { preparedPrompt = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Готовый запрос (можно изменить)") },
                                    minLines = 4,
                                    maxLines = 9
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { clipboard.setText(AnnotatedString(preparedPrompt)); status = "Запрос скопирован. Открой DeepSeek и вставь его." }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Копировать")
                                    }
                                    OutlinedButton(onClick = { sharePrompt() }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Поделиться")
                                    }
                                }
                                OutlinedButton(onClick = { openDeepSeek() }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Открыть DeepSeek")
                                }
                                Text("В DeepSeek нажми «Отправить», дождись ответа и скопируй его сюда.", style = MaterialTheme.typography.bodySmall)
                            }

                            Text("3. Вставь ответ DeepSeek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = responseText,
                                onValueChange = { responseText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ответ нейросети") },
                                placeholder = { Text("Вставь сюда ответ из DeepSeek…") },
                                minLines = 4,
                                maxLines = 12
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (responseText.isBlank()) status = "Сначала вставь ответ DeepSeek."
                                        else { clipboard.setText(AnnotatedString(responseText)); status = "Ответ скопирован в буфер обмена." }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Default.ContentCopy, null); Text("Копировать ответ") }
                                Button(
                                    onClick = {
                                        if (responseText.isBlank()) status = "Сначала вставь ответ DeepSeek."
                                        else if (selectedId == null) status = "Сначала выбери или создай проект."
                                        else {
                                            val separator = if (projectText.isBlank()) "" else "\n\n"
                                            projectText += separator + "## Ответ DeepSeek\n\n" + responseText.trim()
                                            saveCurrentProject()
                                            responseText = ""
                                            status = "Ответ добавлен в рукопись и сохранён на устройстве."
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Default.Save, null); Text("В рукопись") }
                            }
                        }
                    }
                    Screen.PROJECTS -> {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Мои книги", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val p = WriterProject(UUID.randomUUID().toString(), "Новая книга", "")
                                store.saveProject(p); refreshProjects(); selectedId = p.id
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
                                saveCurrentProject()
                                status = "Рукопись сохранена на устройстве."
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("Сохранить") }
                            OutlinedButton(onClick = {
                                task = "Отредактируй этот текст. Сначала перечисли важные проблемы, затем предложи улучшенную версию, сохранив авторский голос."
                                preparedPrompt = makePrompt() + "\n\nТЕКСТ ДЛЯ РЕДАКТИРОВАНИЯ:\n" + projectText.takeLast(14000)
                                screen = Screen.HOME
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text("Подготовить ИИ-редактуру") }
                        }
                    }
                    Screen.SETTINGS -> {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("О приложении", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Agent008 — локальная писательская мастерская. Проекты и рукописи сохраняются на устройстве.")
                            Text("Приложение не вызывает DeepSeek API, не требует API-ключа и не отправляет рукописи автоматически. Ты сам передаёшь подготовленный запрос в официальное приложение или сайт DeepSeek.")
                            Text("Рабочий процесс: подготовить запрос → скопировать или поделиться → отправить в DeepSeek → скопировать ответ → вставить в Agent008 → сохранить в проект.")
                            Text("Версия 0.2.0")
                            OutlinedButton(onClick = { openDeepSeek() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Открыть DeepSeek")
                            }
                        }
                    }
                }
            }
        }
    }
}
