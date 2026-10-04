/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.ResolveHistoryEntity
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.ResolveHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 解析历史页（主页右上角「历史」入口）：最近 [ResolveHistoryEntity.MAX_ENTRIES] 次成功解析记录。
 *
 * 功能：关键词搜索（标题 / 链接 / 提取码）、平台筛选、按「今天 / 昨天 / 日期」分组、
 * 点击再次解析、长按菜单（解析 / 复制链接 / 复制链接+提取码 / 删除）、清空全部。
 *
 * 只有「在解析页点开始解析并成功拿到分享文件目录（或 GitHub 仓库根）」才产生记录，
 * 解析失败不计入；同一链接重复解析只置顶并累计次数（见 ResolveViewModel.recordResolveHistory）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ResolveHistoryViewModel,
    onBack: () -> Unit,
    /** 点击历史记录 → 关闭本页并切到解析页自动解析该链接 */
    onResolve: (link: String, pwd: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current

    // 独立全屏覆盖页：自带 Snackbar 宿主（覆盖层会遮挡主页 Scaffold 的 SnackbarHost）
    val snackbarHostState = rememberGlobalSnackbarHostState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedPlatform by rememberSaveable { mutableStateOf<String?>(null) }
    var menuTarget by remember { mutableStateOf<ResolveHistoryEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 筛选胶囊只列「最近记录里真实出现过的平台」，避免出现永远匹配不到的空胶囊
    val platforms = remember(history) {
        history.map { it.platform }.filter { it.isNotBlank() }.distinct()
    }
    val filtered = remember(history, query, selectedPlatform) {
        val keyword = query.trim().lowercase()
        history.filter { entry ->
            (selectedPlatform == null || entry.platform == selectedPlatform) &&
                (
                    keyword.isEmpty() ||
                        entry.title.lowercase().contains(keyword) ||
                        entry.link.lowercase().contains(keyword) ||
                        entry.pwd.lowercase().contains(keyword)
                    )
        }
    }
    // 列表本来按时间倒序，groupBy 用 LinkedHashMap 保序 → 分组天然从「今天」往更早排
    val grouped = remember(filtered) { filtered.groupBy { historyDayLabel(it.resolveTime) } }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("解析历史", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 没有记录时不显示清空入口
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空解析历史")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (history.isEmpty()) {
                item(key = "empty") { EmptyHistory() }
            } else {
                // 搜索 + 平台筛选：作为列表头部，随列表一起上下滚动
                item(key = "filter") {
                    HistoryFilterBar(
                        query = query,
                        onQueryChange = { query = it },
                        platforms = platforms,
                        selectedPlatform = selectedPlatform,
                        onSelectPlatform = { selectedPlatform = it }
                    )
                }
                if (filtered.isEmpty()) {
                    item(key = "no-match") {
                        NoMatchHistory(
                            onReset = {
                                query = ""
                                selectedPlatform = null
                            }
                        )
                    }
                } else {
                    grouped.forEach { (day, entries) ->
                        item(key = "day-$day") { DayHeader(day = day, count = entries.size) }
                        items(entries, key = { it.id }) { entry ->
                            HistoryRow(
                                history = entry,
                                onClick = { onResolve(entry.link, entry.pwd) },
                                onLongClick = { menuTarget = entry }
                            )
                        }
                    }
                    item(key = "footer") { HistoryFooter(shown = filtered.size, total = history.size) }
                }
            }
        }
    }

    // 长按菜单
    menuTarget?.let { target ->
        HistoryActionDialog(
            history = target,
            onResolve = {
                menuTarget = null
                onResolve(target.link, target.pwd)
            },
            onCopyLink = {
                menuTarget = null
                copyToClipboard(context, target.link)
                SnackbarController.show("链接已复制")
            },
            onCopyLinkWithPwd = {
                menuTarget = null
                copyToClipboard(context, historyLinkWithPwd(target))
                SnackbarController.show("链接与提取码已复制")
            },
            onDelete = {
                menuTarget = null
                viewModel.delete(target.id)
            },
            onDismiss = { menuTarget = null }
        )
    }

    // 清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空解析历史") },
            text = { Text("将删除全部 ${history.size} 条解析历史，此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 搜索框 + 平台筛选胶囊：只有出现过的平台才给胶囊，只有一个平台时不显示筛选行 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    platforms: List<String>,
    selectedPlatform: String?,
    onSelectPlatform: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            placeholder = { Text("搜索标题 / 链接 / 提取码") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除搜索")
                    }
                }
            }
        )
        if (platforms.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPlatform == null,
                    onClick = { onSelectPlatform(null) },
                    label = { Text("全部") }
                )
                platforms.forEach { platform ->
                    FilterChip(
                        selected = selectedPlatform == platform,
                        onClick = { onSelectPlatform(platform) },
                        label = { Text(historyPlatformLabel(platform)) }
                    )
                }
            }
        }
    }
}

/** 日期分组标题：今天 / 昨天 / 具体日期 + 该组条数 */
@Composable
private fun DayHeader(day: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** 单条历史：平台标签 + 相对时间 + 标题 + 链接 + （条目数 / 解析次数 / 提取码）徽标 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(
    history: ResolveHistoryEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (history.platform.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = historyPlatformLabel(history.platform),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = historyRelativeTime(history.resolveTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = history.title.ifBlank { history.link },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (history.title.isNotBlank() && history.title != history.link) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = history.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val badges = buildList {
                if (history.fileCount > 0) add("${history.fileCount} 个条目")
                if (history.parseCount > 1) add("已解析 ${history.parseCount} 次")
                if (history.pwd.isNotBlank()) add("提取码 ${history.pwd}")
            }
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badges.forEach { badge -> HistoryBadge(badge) }
                }
            }
        }
    }
}

/** 行内小徽标（条目数 / 解析次数 / 提取码） */
@Composable
private fun HistoryBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 空状态：一次都没解析成功过 */
@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "还没有解析记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "成功解析出文件目录的分享链接会记录在这里，最多保留 ${ResolveHistoryEntity.MAX_ENTRIES} 条，方便一键再解析",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

/** 空状态：有历史但当前筛选条件下没有匹配 */
@Composable
private fun NoMatchHistory(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "没有匹配的解析记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onReset) { Text("清除筛选条件") }
    }
}

/** 列表底部说明：当前展示条数与上限 */
@Composable
private fun HistoryFooter(shown: Int, total: Int) {
    Text(
        text = if (shown == total) {
            "共 $total 条 · 最多保留 ${ResolveHistoryEntity.MAX_ENTRIES} 条解析记录"
        } else {
            "筛选出 $shown 条 / 共 $total 条 · 最多保留 ${ResolveHistoryEntity.MAX_ENTRIES} 条"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

/** 长按菜单：解析 / 复制链接 / 复制链接+提取码 / 删除 */
@Composable
private fun HistoryActionDialog(
    history: ResolveHistoryEntity,
    onResolve: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyLinkWithPwd: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = history.title.ifBlank { history.link },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                TextButton(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解析")
                }
                TextButton(onClick = onCopyLink, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("复制链接")
                }
                // 有提取码时才给「链接+提取码」入口：拼出来的文案可直接粘给别人
                if (history.pwd.isNotBlank()) {
                    TextButton(onClick = onCopyLinkWithPwd, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制链接 + 提取码")
                    }
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 复制用文案：链接 + 提取码（无提取码时就是链接本身） */
private fun historyLinkWithPwd(history: ResolveHistoryEntity): String =
    if (history.pwd.isBlank()) history.link else "${history.link} 提取码：${history.pwd}"

/** 平台枚举名 → 展示名（历史表里存的是 SharePlatform.name） */
internal fun historyPlatformLabel(platform: String): String = when (platform) {
    "QUARK" -> "夸克网盘"
    "UC" -> "UC网盘"
    "XUNLEI" -> "迅雷网盘"
    "BAIDU" -> "百度网盘"
    "C139" -> "139网盘"
    "PAN123" -> "123云盘"
    "PAN115" -> "115网盘"
    "GITHUB" -> "GitHub"
    else -> "网盘"
}

/**
 * 日期分组标题：今天 / 昨天 / 同年「M月d日」/ 跨年「yyyy年M月d日」。
 * 用「年 + 一年中的第几天」算差值，避免按毫秒差 24h 判断导致跨零点分组错位。
 */
internal fun historyDayLabel(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val dayOfNow = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)
    val dayOfThen = then.get(Calendar.YEAR) * 1000 + then.get(Calendar.DAY_OF_YEAR)
    return when (dayOfNow - dayOfThen) {
        0 -> "今天"
        1 -> "昨天"
        else -> if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(time))
        } else {
            SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(time))
        }
    }
}

/**
 * 相对时间：1 分钟内「刚刚」/ 1 小时内「N 分钟前」/ 当天内「N 小时前」，
 * 更早的显示具体时刻（分组标题已经给出日期，这里只补「几点几分」）。
 */
internal fun historyRelativeTime(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        historyDayLabel(time) == "今天" -> "${diff / 3_600_000L} 小时前"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_history", text))
}
