package com.example.dsh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DSH Client") },
                actions = {
                    Text(vm.status, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.newChat() }) { Text("新会话") }
                    IconButton(onClick = { vm.showSettings = true }) { Text("设置") }
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                TextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.send() }) { Text("发送") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.hasPending) {
                PendingPanel(
                    vm,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(vm.messages, key = { it.id }) { m ->
                    val bg = if (m.role == "user") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    Surface(
                        tonalElevation = 2.dp,
                        color = bg,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.role, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(m.text + if (m.streaming) "▌" else "")
                        }
                    }
                }
            }
        }
        LaunchedEffect(vm.messages.size) {
            scope.launch { if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.size - 1) }
        }
    }

    if (vm.showSettings) {
        AlertDialog(
            onDismissRequest = { vm.showSettings = false },
            title = { Text("连接设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vm.baseUrl, { vm.baseUrl = it }, label = { Text("域名 (https://...)") })
                    OutlinedTextField(vm.username, { vm.username = it }, label = { Text("用户名") })
                    OutlinedTextField(vm.password, { vm.password = it }, label = { Text("密码") })
                }
            },
            confirmButton = { Button(onClick = { vm.saveSettings() }) { Text("保存") } },
            dismissButton = { Button(onClick = { vm.connect() }) { Text("保存并连接") } },
        )
    }
}

@Composable
private fun PendingPanel(vm: ChatViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.approvals.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("需要授权执行: ${item.toolName}", style = MaterialTheme.typography.titleSmall)
                    item.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.respondApproval(item, "allowed-once") }) { Text("允许一次") }
                        Button(onClick = { vm.respondApproval(item, "rejected") }) { Text("拒绝") }
                    }
                }
            }
        }
        vm.questions.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agent 向你提问", style = MaterialTheme.typography.titleSmall)
                    item.questions.forEach { qi ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(qi.question, style = MaterialTheme.typography.bodyMedium)
                            qi.header?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            qi.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            qi.options?.forEach { opt ->
                                val selected = vm.selections[qi.id].orEmpty().contains(opt.label)
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.toggleOption(qi.id, opt.label, qi.multiSelect) },
                                    label = {
                                        Column {
                                            Text(opt.label)
                                            opt.description?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    },
                                )
                            }
                            OutlinedTextField(
                                value = vm.customText[qi.id].orEmpty(),
                                onValueChange = { vm.setCustom(qi.id, it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("或填写自定义回答…") },
                                singleLine = true,
                            )
                        }
                    }
                    Button(onClick = { vm.respondQuestion(item) }) { Text("提交回答") }
                }
            }
        }
    }
}
