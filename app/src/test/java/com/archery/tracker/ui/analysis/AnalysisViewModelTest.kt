package com.archery.tracker.ui.analysis

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto
import com.archery.tracker.data.remote.SessionWithRoundsDto
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import com.archery.tracker.data.remote.SyncResponseDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import retrofit2.Response

private val emptyStats = StatsResponseDto(
    roundCount = 0,
    gap = GapViewDto(null, null, null, emptyList(), false, "Needs history"),
    trend = TrendViewDto(emptyList(), emptyList(), emptyList(), emptyList(),
        com.archery.tracker.data.remote.BestMarkersDto(null, null),
        com.archery.tracker.data.remote.BestMarkersDto(null, null), "Needs history"),
    consistency = ConsistencyViewDto(emptyList(), 0.0, 0.0, 0.0, emptyList(), "Needs history"),
    patterns = PatternsViewDto(emptyList(), emptyList(), "Needs history"),
)

private class StubApi(private val statsResult: () -> StatsResponseDto) : ArcheryApi {
    override suspend fun listSessions(type: String?, from: String?, to: String?, timeOfDay: String?, targetPosition: String?, arrowSet: String?): List<SessionWithRoundsDto> = emptyList()
    override suspend fun putSession(id: String, session: SessionDto): SessionDto = session
    override suspend fun deleteSession(id: String): Response<Unit> = Response.success<Unit>(204, null)
    override suspend fun putRound(id: String, round: RoundDto): RoundDto = round
    override suspend fun sync(request: SyncRequestDto): SyncResponseDto = SyncResponseDto(0, 0)
    override suspend fun getStats(type: String?, from: String?, to: String?, timeOfDay: String?, targetPosition: String?, arrowSet: String?): StatsResponseDto = statsResult()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalysisViewModelTest {

    private lateinit var db: ArcheryDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads stats successfully`() = runTest(dispatcher) {
        val repository = ArcheryRepository(db.archeryDao(), StubApi { emptyStats })
        val viewModel = AnalysisViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.stats?.roundCount)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `surfaces an error when the network call fails`() = runTest(dispatcher) {
        val repository = ArcheryRepository(db.archeryDao(), StubApi { throw java.io.IOException("offline") })
        val viewModel = AnalysisViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }
}
