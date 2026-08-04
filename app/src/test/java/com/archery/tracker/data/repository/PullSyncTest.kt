package com.archery.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Arrow
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.local.RoundEntity
import com.archery.tracker.data.local.SessionEntity
import com.archery.tracker.data.remote.ArrowDto
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionWithRoundsDto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PullSyncTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() = db.close()

    private fun serverSession(
        id: String = "s1", arrowSet: String = "ACC", updatedAt: String = "2026-08-02T00:00:00Z",
        rounds: List<RoundDto> = emptyList(),
    ) = SessionWithRoundsDto(
        id = id, userId = "archer", date = "2026-08-02", type = "practice", timeOfDay = "morning",
        arrowSet = arrowSet, poundage = 50.0, notes = null, updatedAt = updatedAt, rounds = rounds,
    )

    private fun serverRound(id: String = "r1", sessionId: String = "s1", updatedAt: String = "2026-08-02T00:00:00Z") =
        RoundDto(
            id = id, sessionId = sessionId, index = 1, targetPosition = "A",
            arrows = listOf(ArrowDto(10, false)), notes = null, updatedAt = updatedAt,
        )

    private fun localSession(
        id: String = "s1", arrowSet: String = "ACC", updatedAt: String = "2026-08-01T00:00:00Z", dirty: Boolean,
    ) = SessionEntity(
        id = id, date = "2026-08-01", type = "practice", timeOfDay = "morning",
        arrowSet = arrowSet, poundage = 50.0, notes = null, updatedAt = updatedAt, dirty = dirty,
    )

    private fun localRound(
        id: String = "r1", sessionId: String = "s1", updatedAt: String = "2026-08-01T00:00:00Z", dirty: Boolean,
    ) = RoundEntity(
        id = id, sessionId = sessionId, index = 1, targetPosition = "A",
        arrows = listOf(Arrow(9, false)), notes = null, updatedAt = updatedAt, dirty = dirty,
    )

    @Test
    fun `inserts a server session that is not present locally`() = runBlocking {
        api.sessionsToReturn = listOf(serverSession(rounds = listOf(serverRound())))

        assertTrue(repository.pullAndMerge().isSuccess)

        val sessions = db.archeryDao().getAllSessionsOnce()
        assertEquals(1, sessions.size)
        assertFalse(sessions[0].dirty)
        assertEquals(1, db.archeryDao().getRoundsForSession("s1").size)
    }

    @Test
    fun `deletes a non-dirty local session absent from the server`() = runBlocking {
        db.archeryDao().upsertSession(localSession(dirty = false))
        api.sessionsToReturn = emptyList()

        repository.pullAndMerge()

        assertTrue(db.archeryDao().getAllSessionsOnce().isEmpty())
    }

    @Test
    fun `keeps a dirty local session absent from the server`() = runBlocking {
        db.archeryDao().upsertSession(localSession(dirty = true))
        api.sessionsToReturn = emptyList()

        repository.pullAndMerge()

        assertEquals(1, db.archeryDao().getAllSessionsOnce().size)
    }

    @Test
    fun `overwrites a non-dirty local session when the server is newer`() = runBlocking {
        db.archeryDao().upsertSession(localSession(arrowSet = "OLD", updatedAt = "2026-08-01T00:00:00Z", dirty = false))
        api.sessionsToReturn = listOf(serverSession(arrowSet = "NEW", updatedAt = "2026-08-02T00:00:00Z"))

        repository.pullAndMerge()

        assertEquals("NEW", db.archeryDao().getAllSessionsOnce().first().arrowSet)
    }

    @Test
    fun `never overwrites a dirty local session even if the server is newer`() = runBlocking {
        db.archeryDao().upsertSession(localSession(arrowSet = "LOCAL", updatedAt = "2026-08-01T00:00:00Z", dirty = true))
        api.sessionsToReturn = listOf(serverSession(arrowSet = "SERVER", updatedAt = "2026-08-02T00:00:00Z"))

        repository.pullAndMerge()

        assertEquals("LOCAL", db.archeryDao().getAllSessionsOnce().first().arrowSet)
    }

    @Test
    fun `deletes a non-dirty local round removed on the server but keeps the session's other rounds`() = runBlocking {
        db.archeryDao().upsertSession(localSession(dirty = false))
        db.archeryDao().upsertRound(localRound(id = "r1", dirty = false))
        db.archeryDao().upsertRound(localRound(id = "r2", dirty = false))
        // Server still has the session but only round r1.
        api.sessionsToReturn = listOf(serverSession(rounds = listOf(serverRound(id = "r1"))))

        repository.pullAndMerge()

        val rounds = db.archeryDao().getRoundsForSession("s1")
        assertEquals(1, rounds.size)
        assertEquals("r1", rounds[0].id)
    }

    @Test
    fun `protects a session with an unpushed round from deletion`() = runBlocking {
        db.archeryDao().upsertSession(localSession(dirty = false))
        db.archeryDao().upsertRound(localRound(id = "r1", dirty = true))
        api.sessionsToReturn = emptyList()

        repository.pullAndMerge()

        assertEquals(1, db.archeryDao().getAllSessionsOnce().size)
        assertNotNull(db.archeryDao().getRoundsForSession("s1").firstOrNull { it.id == "r1" })
    }

    @Test
    fun `surfaces a pull failure as a failed result without mutating local data`() = runBlocking {
        db.archeryDao().upsertSession(localSession(dirty = false))
        api.listShouldFail = true

        assertTrue(repository.pullAndMerge().isFailure)
        assertEquals(1, db.archeryDao().getAllSessionsOnce().size)
    }

    @Test
    fun `compares timestamps as instants so sub-millisecond precision is not inverted`() = runBlocking {
        // "…100Z" sorts lexically AFTER "…100001Z" even though it is chronologically EARLIER.
        db.archeryDao().upsertSession(localSession(arrowSet = "OLD", updatedAt = "2026-08-02T00:00:00.100Z", dirty = false))
        api.sessionsToReturn = listOf(serverSession(arrowSet = "NEW", updatedAt = "2026-08-02T00:00:00.100001Z"))

        repository.pullAndMerge()

        assertEquals("NEW", db.archeryDao().getAllSessionsOnce().first().arrowSet)
    }

    @Test
    fun `a full sync cycle keeps a session whose round was dirty at the start even after the push clears it`() = runBlocking {
        // Session is clean locally but has an unpushed (dirty) round; meanwhile it was deleted on the
        // server. The push clears the round's dirty flag, but sync() must still protect the session.
        db.archeryDao().upsertSession(localSession(dirty = false))
        db.archeryDao().upsertRound(localRound(id = "r1", dirty = true))
        api.sessionsToReturn = emptyList()

        assertTrue(repository.sync().isSuccess)

        assertEquals(1, db.archeryDao().getAllSessionsOnce().size)
        assertNotNull(db.archeryDao().getRoundsForSession("s1").firstOrNull { it.id == "r1" })
    }
}
