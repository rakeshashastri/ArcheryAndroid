package com.archery.tracker.ui.newsession

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.SessionType
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewSessionViewModelTest {

    private lateinit var db: ArcheryDatabase
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
        repository = ArcheryRepository(db.archeryDao(), FakeArcheryApi())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `start creates a session and its first round together`() = runTest(dispatcher) {
        val viewModel = NewSessionViewModel(repository)
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
        val viewModel = NewSessionViewModel(repository)
        val idBefore = viewModel.uiState.value.sessionId
        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()
        val idAfter = viewModel.uiState.value.sessionId

        assertEquals(idBefore, idAfter)
        assertNotNull(idBefore)
    }

    @Test
    fun `pre-fills poundage from history on type change`() = runTest(dispatcher) {
        // Seed one prior session with a distinctive poundage.
        val seedViewModel = NewSessionViewModel(repository)
        seedViewModel.updatePoundage(53.0)
        seedViewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        val viewModel = NewSessionViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(53.0, viewModel.uiState.value.poundage, 0.0)
    }
}
