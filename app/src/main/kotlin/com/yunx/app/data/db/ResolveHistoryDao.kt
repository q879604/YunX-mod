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

package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResolveHistoryDao {

    /** 最近的解析历史（按时间倒序，条数由调用方按上限传入） */
    @Query("SELECT * FROM resolve_history ORDER BY resolveTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ResolveHistoryEntity>>

    /** 按链接查已有记录：命中则不新增，改为刷新时间置顶 */
    @Query("SELECT * FROM resolve_history WHERE link = :link LIMIT 1")
    suspend fun findByLink(link: String): ResolveHistoryEntity?

    @Insert
    suspend fun insert(history: ResolveHistoryEntity): Long

    /**
     * 同一链接再次解析：刷新时间 / 标题 / 提取码 / 平台 / 条目数并置顶，同时把解析次数 +1。
     * 右侧的 `parseCount = parseCount + 1` 读的是该行更新前的值（SQL UPDATE 语义），不是自增竞争。
     */
    @Query(
        "UPDATE resolve_history SET resolveTime = :time, title = :title, pwd = :pwd, " +
            "platform = :platform, fileCount = :fileCount, parseCount = parseCount + 1 " +
            "WHERE id = :id"
    )
    suspend fun refresh(
        id: Long,
        time: Long,
        title: String,
        pwd: String,
        platform: String,
        fileCount: Int
    )

    /** 只保留最近 keep 条，淘汰其余最旧记录 */
    @Query(
        "DELETE FROM resolve_history WHERE id NOT IN " +
            "(SELECT id FROM resolve_history ORDER BY resolveTime DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM resolve_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM resolve_history")
    suspend fun clearAll()
}
