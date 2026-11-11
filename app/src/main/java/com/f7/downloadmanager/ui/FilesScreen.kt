package com.f7.downloadmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.f7.downloadmanager.F7ViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.Categories
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Fmt
import com.f7.downloadmanager.ui.theme.LocalSkin
import java.io.File

@Composable
fun FilesScreen(
    items: List<DownloadItem>,
    freeBytes: Long,
    vm: F7ViewModel,
    onOpen: (DownloadItem) -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingRemove by remember { mutableStateOf<DownloadItem?>(null) }

    val completed = remember(items) {
        items.filter { it.status == DStatus.COMPLETED || it.status == DStatus.FAILED }
    }
    val used = completed.sumOf { it.totalBytes }
    val duplicates = remember(completed) {
        completed.filter { it.checksum != null }
            .groupBy { it.checksum }
            .filterValues { it.size > 1 }
            .flatMap { it.value }
            .map { it.id }
            .toSet()
    }
    val counts = remember(completed) { completed.groupingBy { it.category }.eachCount() }
    val availableCategories = remember(completed) {
        Categories.all.filter { (counts[it] ?: 0) > 0 }
    }
    val visible = remember(completed, query, category, sort) {
        val needle = query.trim().lowercase()
        val filtered = completed
            .filter { category == null || it.category == category }
            .filter {
                needle.isEmpty() ||
                    it.fileName.lowercase().contains(needle) ||
                    it.url.lowercase().contains(needle)
            }
        when (sort) {
            1 -> filtered.sortedBy { it.fileName.lowercase() }
            2 -> filtered.sortedByDescending { it.totalBytes }
            else -> filtered.sortedByDescending { it.completedAt ?: it.createdAt }
        }
    }
    val sortLabels = listOf(R.string.sort_newest, R.string.sort_name, R.string.sort_size)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = androidx.compose.ui.res.stringResource(R.string.files_title),
            subtitle = androidx.compose.ui.res.stringResource(R.string.files_subtitle),
            trailing = null,
        )

        F7Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    StatBlock(
                        label = androidx.compose.ui.res.stringResource(R.string.files_used),
                        value = Fmt.bytes(used),
                        modifier = Modifier.weight(1f),
                        emphasize = true,
                    )
                    StatBlock(
                        label = androidx.compose.ui.res.stringResource(R.string.files_free),
                        value = Fmt.bytes(freeBytes),
                        modifier = Modifier.weight(1f),
                        emphasize = true,
                    )
                    StatBlock(
                        label = androidx.compose.ui.res.stringResource(R.string.files_stat),
                        value = "${completed.size}",
                        modifier = Modifier.weight(1f),
                        emphasize = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val total = (used + freeBytes).coerceAtLeast(1L)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(skin.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((used.toFloat() / total).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(skin.primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                shape = CircleShape,
                leadingIcon = {
                    Icon(F7Icons.Search, null, tint = skin.muted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconCircleButton(
                            icon = F7Icons.Close,
                            contentDescription = "Clear search",
                            onClick = { query = "" },
                            size = 30.dp,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                            tint = skin.muted,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = skin.primary,
                    unfocusedBorderColor = skin.hairline,
                    cursorColor = skin.primary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            FilterPill(
                text = androidx.compose.ui.res.stringResource(
                    R.string.sort_label,
                    androidx.compose.ui.res.stringResource(sortLabels[sort]),
                ),
                selected = false,
                onClick = { sort = (sort + 1) % sortLabels.size },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                text = androidx.compose.ui.res.stringResource(R.string.filter_all),
                selected = category == null,
                onClick = { category = null },
            )
            availableCategories.forEach { cat ->
                FilterPill(
                    text = "${categoryLabel(cat)}  ${counts[cat] ?: 0}",
                    selected = category == cat,
                    onClick = { category = if (category == cat) null else cat },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    icon = F7Icons.Folder,
                    title = androidx.compose.ui.res.stringResource(
                        if (completed.isEmpty()) R.string.files_empty_title else R.string.files_no_match_title,
                    ),
                    body = androidx.compose.ui.res.stringResource(
                        if (completed.isEmpty()) R.string.files_empty_body else R.string.files_no_match_body,
                    ),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { item ->
                    F7Card(
                        Modifier.fillMaxWidth(),
                        onClick = { if (item.location != null) onOpen(item) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryTile(item.category)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = skin.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${Fmt.bytes(item.totalBytes)}  ·  ${Fmt.date(item.completedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.id in duplicates || !item.checksum.isNullOrBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (item.id in duplicates) {
                                            Pill(
                                                text = androidx.compose.ui.res.stringResource(R.string.files_duplicate),
                                                background = skin.danger.copy(alpha = 0.12f),
                                                contentColor = skin.danger,
                                            )
                                        }
                                        if (!item.checksum.isNullOrBlank()) {
                                            Pill(
                                                text = androidx.compose.ui.res.stringResource(R.string.files_verified),
                                                background = skin.primary.copy(alpha = 0.10f),
                                                contentColor = skin.primary,
                                            )
                                        }
                                    }
                                }
                            }
                            Box {
                                IconCircleButton(
                                    icon = F7Icons.More,
                                    contentDescription = "More",
                                    onClick = { menuFor = item.id },
                                    background = skin.cardAlt,
                                )
                                DropdownMenu(
                                    expanded = menuFor == item.id,
                                    onDismissRequest = { menuFor = null },
                                    containerColor = skin.card,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.action_open), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            onOpen(item)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.files_verify), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            vm.verify(item)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.action_share), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            shareDownloaded(context, item)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.files_copy_path), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            clipboard.setText(AnnotatedString(item.location ?: item.url))
                                            vm.notify(context.getString(R.string.toast_copied))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.files_delete_file), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            confirmDelete = item
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(androidx.compose.ui.res.stringResource(R.string.files_delete_entry), style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            menuFor = null
                                            pendingRemove = item
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = skin.card,
            title = { Text(androidx.compose.ui.res.stringResource(R.string.files_delete_file), style = MaterialTheme.typography.titleLarge) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.files_delete_file_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_delete),
                    onClick = {
                        vm.deleteFile(item)
                        confirmDelete = null
                    },
                )
            },
            dismissButton = {
                GhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_cancel),
                    onClick = { confirmDelete = null },
                )
            },
        )
    }

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = skin.card,
            title = { Text(androidx.compose.ui.res.stringResource(R.string.remove_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.remove_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_remove),
                    onClick = {
                        vm.remove(item)
                        pendingRemove = null
                    },
                )
            },
            dismissButton = {
                GhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_cancel),
                    onClick = { pendingRemove = null },
                )
            },
        )
    }
}

fun openDownloaded(context: Context, item: DownloadItem) {
    val location = item.location ?: return
    val uri: Uri = if (location.startsWith("content://")) {
        Uri.parse(location)
    } else {
        val file = File(location)
        if (!file.exists()) return
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val extension = item.fileName.substringAfterLast('.', "").lowercase()
    val mime = item.mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        val fallback = Intent(Intent.ACTION_VIEW).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

private fun shareDownloaded(context: Context, item: DownloadItem) {
    val location = item.location ?: return
    val uri: Uri = if (location.startsWith("content://")) {
        Uri.parse(location)
    } else {
        val file = File(location)
        if (!file.exists()) return
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType(item.mime ?: "application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
