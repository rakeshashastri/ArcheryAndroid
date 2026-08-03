package com.archery.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArcheryRepositoryTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository

    private fun session(id: String = "s1") = Session(
        id = id, date = "2026-08-01", type = SessionType.PRACTICE, timeOfDay = TimeOfDay.MORNING,
        arrowSet = "ACC", poundage = 50.0, notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun round(id: String = "r1", sessionId: String = "s1") = Round(
        id = id, sessionId = sessionId, index = 1, targetPosition = TargetPosition.A,
        arrows = emptyList(), notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createSessionWithFirstRound persists both rows dirty`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        val sessions = repository.sessions().first()
        assertEquals(1, sessions.size)
        assertEquals(1, sessions[0].rounds.size)
    }

    @Test
    fun `syncDirty sends only dirty rows and clears their dirty flag on success`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())

        val result = repository.syncDirty()

        assertTrue(result.isSuccess)
        assertEquals(1, api.syncCalls.size)
        assertEquals(1, api.syncCalls[0].sessions.size)
        assertEquals(1, api.syncCalls[0].rounds.size)
        assertTrue(db.archeryDao().getDirtySessions().isEmpty())
        assertTrue(db.archeryDao().getDirtyRounds().isEmpty())
    }

    @Test
    fun `syncDirty leaves rows dirty when the network call fails`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        api.syncShouldFail = true

        val result = repository.syncDirty()

        assertTrue(result.isFailure)
        assertFalse(db.archeryDao().getDirtySessions().isEmpty())
        assertFalse(db.archeryDao().getDirtyRounds().isEmpty())
    }

    @Test
    fun `syncDirty is a no-op when nothing is dirty`() = runBlocking {
        val result = repository.syncDirty()
        assertTrue(result.isSuccess)
        assertTrue(api.syncCalls.isEmpty())
    }

    @Test
    fun `saveRound marks the round dirty for the next sync`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        repository.syncDirty()

        repository.saveRound(round().copy(arrows = listOf(Arrow(9, false))))

        assertEquals(1, db.archeryDao().getDirtyRounds().size)
    }

    @Test
    fun `deleteSession removes local rows only when the network call succeeds`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())

        val result = repository.deleteSession("s1")

        assertTrue(result.isSuccess)
        assertEquals(1, api.deleteCalls.size)
        assertTrue(repository.sessions().first().isEmpty())
    }

    @Test
    fun `deleteSession keeps local rows when the network call fails`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        api.deleteShouldFail = true

        val result = repository.deleteSession("s1")

        assertTrue(result.isFailure)
        assertEquals(1, repository.sessions().first().size)
    }
}
