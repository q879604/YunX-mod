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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 解析历史（Room 持久化）：主页（解析页）右上角「历史」入口的数据源。
 *
 * 写入时机：在解析页输入链接并点击「开始解析」，且**成功拿到分享文件目录（或 GitHub 仓库根）**之后。
 * 解析失败、链接无法识别、取文件列表失败都不产生记录；进入分享内的子目录也不算一次新的解析。
 *
 * 同一链接重复解析不新增记录，只刷新时间置顶并把 [parseCount] +1；最多保留 [MAX_ENTRIES] 条
 * （超限由 DAO 的 `trimTo` 淘汰最旧，读取侧按同一常量取最近 N 条）。
 */
@Entity(tableName = "resolve_history")
data class ResolveHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 完整分享链接 / 分享文案（再次解析用，原样保存） */
    val link: String,
    /** 提取码（可选） */
    val pwd: String = "",
    /** 平台枚举名（QUARK/UC/XUNLEI/BAIDU/C139/PAN123/PAN115/GITHUB），未知为空串 */
    val platform: String = "",
    /** 分享标题（解析成功后回填，可为空，展示时回退为链接） */
    val title: String = "",
    /** 解析成功时根目录的条目数；GitHub 没有「文件数」概念，传 0 表示不展示 */
    val fileCount: Int = 0,
    /** 同一链接被解析过的次数（重复解析置顶时 +1），用于「已解析 N 次」徽标 */
    val parseCount: Int = 1,
    /** 最近一次解析成功的时间（用于倒序排列与淘汰） */
    val resolveTime: Long = System.currentTimeMillis()
) {
    companion object {
        /** 历史条数上限：超出后淘汰最旧记录（即「近期 40 次解析」） */
        const val MAX_ENTRIES = 40
    }
}
