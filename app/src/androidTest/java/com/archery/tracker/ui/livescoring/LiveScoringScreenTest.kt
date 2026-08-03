package com.archery.tracker.ui.livescoring

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveScoringScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: ArcheryDatabase
    private lateinit var repository: ArcheryRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = ArcheryRepository(db.archeryDao(), FakeArcheryApi())
        repository.createSessionWithFirstRound(
            Session("s1", "2026-08-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-08-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-08-01T00:00:00Z"),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tappingNineUpdatesTheDisplayedEndTotalAndPersistsToRoom() {
        val viewModel = LiveScoringViewModel(ApplicationProvider.getApplicationContext(), repository, "s1", "r1")
        composeRule.setContent { LiveScoringScreenContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("End total: 27").assertExists()

        val persisted = runBlocking { db.archeryDao().getRoundsForSession("s1")[0] }
        assertEquals(3, persisted.arrows.size)
    }

    @Test
    fun undoRemovesTheLastArrow() {
        val viewModel = LiveScoringViewModel(ApplicationProvider.getApplicationContext(), repository, "s1", "r1")
        composeRule.setContent { LiveScoringScreenContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("End total: 9").assertExists()
    }
}
