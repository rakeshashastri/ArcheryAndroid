package com.archery.tracker.ui.sessiondetail

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import com.archery.tracker.sync.SyncWorkerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDetailViewModelTest {

    private lateinit var application: Application
    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(application, ArcheryDatabase::class.java)
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

        val workManagerConfig = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(SyncWorkerFactory(repository))
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(application, workManagerConfig)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the session and its rounds`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(application, repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("s1", viewModel.uiState.value.session?.id)
        assertEquals(1, viewModel.uiState.value.rounds.size)
    }

    @Test
    fun `offers to add a round while under the practice limit and returns the new round id`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(application, repository, "s1")
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
        val viewModel = SessionDetailViewModel(application, repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        var deleted = false
        viewModel.deleteSession { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(db.archeryDao().getAllSessions().first().isEmpty())
    }

    @Test
    fun `deleteSession surfaces an error and keeps the session when the network call fails`() = runTest(dispatcher) {
        api.deleteShouldFail = true
        val viewModel = SessionDetailViewModel(application, repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        var deleted = false
        viewModel.deleteSession { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(deleted)
        assertNotNull(viewModel.uiState.value.deleteError)
        assertEquals(1, db.archeryDao().getAllSessions().first().size)
    }

    @Test
    fun `a stale delete error is cleared once a round is successfully added`() = runTest(dispatcher) {
        api.deleteShouldFail = true
        val viewModel = SessionDetailViewModel(application, repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteSession {}
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.deleteError)

        viewModel.addRound()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteError)
    }
}
