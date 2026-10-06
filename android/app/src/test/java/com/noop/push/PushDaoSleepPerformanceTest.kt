package com.noop.push

import androidx.room.Room
import com.noop.data.DailyMetric
import com.noop.data.MetricSeriesRow
import com.noop.data.WhoopDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PushDaoSleepPerformanceTest {
    @Test
    fun v11ProjectsOnlyTheMatchingMetricSeriesScoreAndV10StaysUnchanged() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WhoopDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val day = "2026-08-18"
            database.whoopDao().upsertDailyMetrics(
                listOf(
                    DailyMetric(deviceId = "strap-noop", day = day, totalSleepMin = 430.0),
                    DailyMetric(deviceId = "other-noop", day = day, totalSleepMin = 420.0),
                ),
            )
            database.whoopDao().upsertMetricSeries(
                listOf(
                    MetricSeriesRow("strap-noop", day, "sleep_performance", 91.5),
                    MetricSeriesRow("other-noop", day, "sleep_performance", 12.0),
                    MetricSeriesRow("strap-noop", day, "recovery", 70.0),
                ),
            )
            val window = PushWindow(day, day, 1L, 2L)
            val source = database.pushDao()

            val legacy = source.mutableRows(
                PushMutableTable.DAILY_METRIC, "strap-noop", window, 10, PushProtocol.VERSION,
            ).single()
            val current = source.mutableRows(
                PushMutableTable.DAILY_METRIC, "strap-noop", window, 10, PushProtocol.LATEST_VERSION,
            ).single()

            assertFalse(legacy.data.containsKey("sleepPerformance"))
            assertTrue(current.data.containsKey("sleepPerformance"))
            assertEquals(91.5, current.data["sleepPerformance"] as Double, 0.0)
            assertNull(current.data["recovery"])
        } finally {
            database.close()
        }
    }

    @Test
    fun v11EmitsNullWhenTheSameSourceHasNoSleepPerformancePoint() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WhoopDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val day = "2026-08-18"
            database.whoopDao().upsertDailyMetrics(
                listOf(DailyMetric(deviceId = "strap-noop", day = day, totalSleepMin = 430.0)),
            )
            val current = database.pushDao().mutableRows(
                PushMutableTable.DAILY_METRIC,
                "strap-noop",
                PushWindow(day, day, 1L, 2L),
                10,
                PushProtocol.LATEST_VERSION,
            ).single()

            assertNull(current.data["sleepPerformance"])
        } finally {
            database.close()
        }
    }
}
