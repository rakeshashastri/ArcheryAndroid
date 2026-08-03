package com.archery.tracker.ui.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `lists sessions with a summary and marks unsynced ones dirty`() = runTest(dispatcher) {
        repository.createSessionWithFirstRound(
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
        val viewModel = HistoryViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val rows = viewModel.rows.value
        assertEquals(1, rows.size)
        assertTrue(rows[0].isDirty)
    }

    @Test
    fun `clears the dirty indicator once synced`() = runTest(dispatcher) {
        repository.createSessionWithFirstRound(
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
        repository.syncDirty()
        val viewModel = HistoryViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.rows.value.none { it.isDirty })
    }
}
