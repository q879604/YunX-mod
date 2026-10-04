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

package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.ResolveHistoryDao
import com.yunx.app.data.db.ResolveHistoryEntity
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 解析历史 ViewModel：历史列表（Room Flow → StateFlow）+ 单条删除 / 清空。
 *
 * 写入由 [ResolveViewModel] 在解析成功时完成，这里只负责读取与维护，
 * 列表长度与写入侧共用同一个上限常量 [ResolveHistoryEntity.MAX_ENTRIES]。
 */
class ResolveHistoryViewModel(private val dao: ResolveHistoryDao) : ViewModel() {

    /** 最近 [ResolveHistoryEntity.MAX_ENTRIES] 条历史，按时间倒序 */
    val history: StateFlow<List<ResolveHistoryEntity>> =
        dao.observeRecent(ResolveHistoryEntity.MAX_ENTRIES)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.delete(id)
            SnackbarController.show("已删除该条解析历史")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.clearAll()
            SnackbarController.show("已清空解析历史")
        }
    }

    class Factory(private val dao: ResolveHistoryDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ResolveHistoryViewModel::class.java))
            return ResolveHistoryViewModel(dao) as T
        }
    }
}
