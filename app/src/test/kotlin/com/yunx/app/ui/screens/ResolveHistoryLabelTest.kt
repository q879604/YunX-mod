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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析历史页的两个纯展示函数（平台名 / 日期分组 / 相对时间）单测。
 *
 * 刻意只断言与「当前时刻」无关的稳定行为（例：10 天前必然落到日期分组），
 * 避免时区、跨零点等边界让用例偶发失败。
 */
class ResolveHistoryLabelTest {

    @Test
    fun platformLabelCoversKnownPlatformsAndUnknownFallback() {
        assertEquals("夸克网盘", historyPlatformLabel("QUARK"))
        assertEquals("115网盘", historyPlatformLabel("PAN115"))
        assertEquals("GitHub", historyPlatformLabel("GITHUB"))
        assertEquals("网盘", historyPlatformLabel(""))
        assertEquals("网盘", historyPlatformLabel("SOMETHING_NEW"))
    }

    @Test
    fun todayIsGroupedAsToday() {
        assertEquals("今天", historyDayLabel(System.currentTimeMillis()))
    }

    @Test
    fun oldEntriesFallBackToDateGroup() {
        val label = historyDayLabel(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000)
        assertTrue(
            "10 天前应回退到日期分组，实际：$label",
            label.matches(Regex("(\\d{4}年)?\\d{1,2}月\\d{1,2}日"))
        )
    }

    @Test
    fun relativeTimeCoversJustNowAndMinutes() {
        val now = System.currentTimeMillis()
        assertEquals("刚刚", historyRelativeTime(now))
        assertEquals("刚刚", historyRelativeTime(now - 30_000L))
        assertEquals("5 分钟前", historyRelativeTime(now - 5L * 60_000))
        assertEquals("59 分钟前", historyRelativeTime(now - 59L * 60_000))
    }

    @Test
    fun olderEntriesUseClockTimeOnly() {
        val label = historyRelativeTime(System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000)
        assertTrue("100 天前应显示具体时刻，实际：$label", label.matches(Regex("\\d{2}:\\d{2}")))
    }
}
