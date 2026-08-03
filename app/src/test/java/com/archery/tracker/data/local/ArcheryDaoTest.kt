package com.archery.tracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Arrow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArcheryDaoTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var dao: ArcheryDao

    private fun session(id: String = "s1", dirty: Boolean = true) = SessionEntity(
        id = id, date = "2026-08-01", type = "practice", timeOfDay = "morning",
        arrowSet = "ACC", poundage = 50.0, notes = null,
        updatedAt = "2026-08-01T00:00:00Z", dirty = dirty,
    )

    private fun round(id: String = "r1", sessionId: String = "s1", dirty: Boolean = true) = RoundEntity(
        id = id, sessionId = sessionId, index = 1, targetPosition = "A",
        arrows = listOf(Arrow(9, false)), notes = null,
        updatedAt = "2026-08-01T00:00:00Z", dirty = dirty,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArcheryDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.archeryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsertSession then getAllSessions returns it`() = runBlocking {
        dao.upsertSession(session())
        val all = dao.getAllSessions().first()
        assertEquals(1, all.size)
        assertEquals("s1", all[0].id)
    }

    @Test
    fun `upsertSession overwrites on repeated insert with the same id`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertSession(session().copy(poundage = 52.0))
        val all = dao.getAllSessions().first()
        assertEquals(1, all.size)
        assertEquals(52.0, all[0].poundage, 0.0)
    }

    @Test
    fun `round-trips the arrows list through the type converter`() = runBlocking {
        dao.upsertSession(session())
        val arrows = listOf(Arrow(10, true), Arrow(9, false), Arrow(0, false))
        dao.upsertRound(round().copy(arrows = arrows))
        val rounds = dao.getRoundsForSession("s1")
        assertEquals(arrows, rounds[0].arrows)
    }

    @Test
    fun `getDirtySessions and getDirtyRounds return only dirty rows`() = runBlocking {
        dao.upsertSession(session(id = "s1", dirty = true))
        dao.upsertSession(session(id = "s2", dirty = false))
        dao.upsertRound(round(id = "r1", sessionId = "s1", dirty = true))
        dao.upsertRound(round(id = "r2", sessionId = "s1", dirty = false))

        assertEquals(listOf("s1"), dao.getDirtySessions().map { it.id })
        assertEquals(listOf("r1"), dao.getDirtyRounds().map { it.id })
    }

    @Test
    fun `clearSessionDirty and clearRoundDirty flip dirty to false`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertRound(round())
        dao.clearSessionDirty("s1")
        dao.clearRoundDirty("r1")
        assertTrue(dao.getDirtySessions().isEmpty())
        assertTrue(dao.getDirtyRounds().isEmpty())
    }

    @Test
    fun `deleteSession removes the session and deleteRoundsForSession removes its rounds`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertRound(round())
        dao.deleteRoundsForSession("s1")
        dao.deleteSession("s1")
        assertTrue(dao.getAllSessions().first().isEmpty())
        assertTrue(dao.getRoundsForSession("s1").isEmpty())
    }
}
