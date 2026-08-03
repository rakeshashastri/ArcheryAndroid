package com.archery.tracker.ui.sessiondetail

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDetailViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

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
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the session and its rounds`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("s1", viewModel.uiState.value.session?.id)
        assertEquals(1, viewModel.uiState.value.rounds.size)
    }

    @Test
    fun `offers to add a round while under the practice limit and returns the new round id`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canAddRound)

        val newRoundId = viewModel.addRound()
        dispatcher.scheduler.advanceUntilIdle()

        val rounds = db.archeryDao().getRoundsForSession("s1")
        assertEquals(2, rounds.size)
        assertTrue(rounds.any { it.id == newRoundId && it.index == 2 })
    }

    @Test
    fun `deleteSession removes the session and its rounds when the network call succeeds`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        var deleted = false
        viewModel.deleteSession { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(db.archeryDao().getAllSessions().first().isEmpty())
    }
}
