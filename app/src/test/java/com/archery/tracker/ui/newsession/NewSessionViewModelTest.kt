package com.archery.tracker.ui.newsession

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.data.local.ArcheryDao
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import com.archery.tracker.sync.SyncWorkerFactory
import java.io.IOException
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewSessionViewModelTest {

    private lateinit var application: Application
    private lateinit var db: ArcheryDatabase
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(application, ArcheryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = ArcheryRepository(db.archeryDao(), FakeArcheryApi())

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
    fun `start creates a session and its first round together`() = runTest(dispatcher) {
        val viewModel = NewSessionViewModel(application, repository)
        viewModel.updateArrowSet("ACC")
        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        val sessions = db.archeryDao().getAllSessions()
        assertEquals(1, sessions.first().let { it.size })
        val rounds = db.archeryDao().getRoundsForSession(sessions.first()[0].id)
        assertEquals(1, rounds.size)
        assertEquals(0, rounds[0].arrows.size)
    }

    @Test
    fun `start reuses the same session id across a retry so a partial failure cannot orphan a session`() = runTest(dispatcher) {
        val flakyRepository = FlakyRepository(db.archeryDao(), FakeArcheryApi())
        val viewModel = NewSessionViewModel(application, flakyRepository)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateArrowSet("ACC")

        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertEquals(0, db.archeryDao().getAllSessions().first().size)

        val sessionIdAfterFailure = viewModel.uiState.value.sessionId
        val roundIdAfterFailure = viewModel.uiState.value.roundId

        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(sessionIdAfterFailure, viewModel.uiState.value.sessionId)
        assertEquals(roundIdAfterFailure, viewModel.uiState.value.roundId)

        val sessions = db.archeryDao().getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(sessionIdAfterFailure, sessions[0].id)

        val rounds = db.archeryDao().getRoundsForSession(sessionIdAfterFailure)
        assertEquals(1, rounds.size)
        assertEquals(roundIdAfterFailure, rounds[0].id)
    }

    @Test
    fun `pre-fills poundage from the most recent prior session`() = runTest(dispatcher) {
        // Seed one prior session with a distinctive poundage.
        val seedViewModel = NewSessionViewModel(application, repository)
        seedViewModel.updateArrowSet("ACC")
        seedViewModel.updatePoundage(53.0)
        seedViewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        val viewModel = NewSessionViewModel(application, repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(53.0, viewModel.uiState.value.poundage, 0.0)
    }

    @Test
    fun `start refuses to create a session with a blank arrow set`() = runTest(dispatcher) {
        val viewModel = NewSessionViewModel(application, repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.started)
        assertEquals(0, db.archeryDao().getAllSessions().first().size)
    }
}

private class FlakyRepository(dao: ArcheryDao, api: ArcheryApi) : ArcheryRepository(dao, api) {
    private var shouldFailNext = true

    override suspend fun createSessionWithFirstRound(session: Session, firstRound: Round) {
        if (shouldFailNext) {
            shouldFailNext = false
            throw IOException("simulated failure on first attempt")
        }
        super.createSessionWithFirstRound(session, firstRound)
    }
}
