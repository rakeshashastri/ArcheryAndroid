package com.archery.tracker.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import androidx.room.Room
import com.archery.tracker.data.local.ArcheryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

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

    @Test
    fun `doWork succeeds when syncDirty succeeds`() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(SyncWorkerFactory(repository))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork retries when syncDirty fails`() = runBlocking {
        api.syncShouldFail = true
        // Force something dirty so syncDirty actually attempts the network call.
        repository.createSessionWithFirstRound(
            com.archery.tracker.core.Session(
                id = "s1", date = "2026-08-01", type = com.archery.tracker.core.SessionType.PRACTICE,
                timeOfDay = com.archery.tracker.core.TimeOfDay.MORNING, arrowSet = "ACC",
                poundage = 50.0, notes = null, updatedAt = "2026-08-01T00:00:00Z",
            ),
            com.archery.tracker.core.Round(
                id = "r1", sessionId = "s1", index = 1,
                targetPosition = com.archery.tracker.core.TargetPosition.A,
                arrows = emptyList(), notes = null, updatedAt = "2026-08-01T00:00:00Z",
            ),
        )

        val worker = TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(SyncWorkerFactory(repository))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
