package com.archery.tracker.ui.livescoring

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
class LiveScoringViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()
    private val sessionId = "s1"
    private val roundId = "r1"

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
        repository.createSessionWithFirstRound(
            Session(sessionId, "2026-08-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-08-01T00:00:00Z"),
            Round(roundId, sessionId, 1, TargetPosition.A, emptyList(), null, "2026-08-01T00:00:00Z"),
        )
        repository.syncDirty()
        dispatcher.scheduler.advanceUntilIdle()
        api.syncCalls.clear()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `each tap is persisted to Room before anything else`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.add(9, isX = false)
        dispatcher.scheduler.advanceUntilIdle()

        val persisted = db.archeryDao().getRoundsForSession(sessionId)[0]
        assertEquals(1, persisted.arrows.size)
        assertEquals(9, persisted.arrows[0].value)
    }

    @Test
    fun `shows a running end total as arrows are entered`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(3) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(27, viewModel.uiState.value.currentEndTotal)
    }

    @Test
    fun `triggers a sync only once a full end of six arrows is reached`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(5) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(api.syncCalls.isEmpty())

        viewModel.add(9, isX = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, api.syncCalls.size)
    }

    @Test
    fun `undoes the last arrow`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(2) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.undo()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(9, viewModel.uiState.value.currentEndTotal)
        assertEquals(1, viewModel.uiState.value.arrows.size)
    }

    @Test
    fun `stops accepting arrows at 36`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(36) { viewModel.add(5, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(180, viewModel.uiState.value.roundTotal)

        viewModel.add(5, isX = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(180, viewModel.uiState.value.roundTotal)
    }
}
