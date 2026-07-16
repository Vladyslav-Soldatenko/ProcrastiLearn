package com.procrastilearn.app.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.StudyPreferencesDataStore
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Wires the real [DayCountersStore] (DataStore-backed) together with a real
 * Room-backed [VocabularyRepositoryImpl] - no mocks - to exercise the exact
 * "Add Cards For Today" flow end-to-end: exhaust the permanent daily limit,
 * add extra cards for today, consume them, and roll over to the next day.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AddCardsForTodayIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var repository: VocabularyRepositoryImpl

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        dayCountersStore = DayCountersStore(StudyPreferencesDataStore(dataStoreContext))

        repository =
            VocabularyRepositoryImpl(
                vocabularyDao = database.vocabularyDao(),
                scheduler = Scheduler.builder().build(),
                prefs = dayCountersStore,
                undoSnapshotDao = database.undoSnapshotDao(),
                appDatabase = database,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    private suspend fun insertNewWords(count: Int) {
        repeat(count) { i ->
            database.vocabularyDao().insertVocabulary(
                VocabularyEntity(
                    id = 0,
                    word = "word$i",
                    translation = "trans$i",
                ),
            )
        }
    }

    private suspend fun reviewNextNewCard() {
        val item = repository.getNextVocabularyItem()
        repository.reviewVocabularyItem(item.id, Rating.GOOD)
    }

    @Test
    fun `add cards for today grants extra new cards and resets on day rollover`() =
        runTest {
            // "set New Per Day = 2 in Settings"
            dayCountersStore.setNewPerDay(2)
            // 5 consumed before rollover (2 permanent + 3 boosted) + 2 consumed after rollover.
            insertNewWords(7)

            // "exhaust it in Dojo"
            reviewNextNewCard()
            reviewNextNewCard()
            assertThat(repository.hasAvailableItems()).isFalse()
            assertThat(dayCountersStore.read().first().newShown).isEqualTo(2)

            // "open Settings -> Add Cards For Today, add 3"
            dayCountersStore.addExtraNewToday(3, availableNew = database.vocabularyDao().countNewTotal())

            // "confirm 3 more cards appear in Dojo"
            assertThat(repository.hasAvailableItems()).isTrue()
            reviewNextNewCard()
            reviewNextNewCard()
            reviewNextNewCard()

            // Permanent limit (2) + boost (3) = 5 new cards consumed; nothing more today.
            assertThat(repository.hasAvailableItems()).isFalse()

            // "advance the device date" - simulated the way ensureDay() itself
            // detects and handles a day change internally.
            val tomorrow = todayStamp() + 1
            dayCountersStore.resetFor(tomorrow)

            // "confirm the allowance returns to 2"
            val countersAfterRollover = dayCountersStore.read().first()
            assertThat(countersAfterRollover.newShown).isEqualTo(0)
            assertThat(countersAfterRollover.extraNewToday).isEqualTo(0)
            assertThat(dayCountersStore.readPolicy().first().newPerDay).isEqualTo(2)

            // Only 2 new cards (the permanent quota) are available, not 5.
            reviewNextNewCard()
            reviewNextNewCard()
            assertThat(repository.hasAvailableItems()).isFalse()
        }

    @Test
    fun `add cards for today cannot grant more than the actual unseen cards in the deck`() =
        runTest {
            dayCountersStore.setNewPerDay(1)
            insertNewWords(4) // Only 4 unseen cards ever exist in the deck.
            // Establish "today" first (mirrors real usage where the day is already
            // current by the time Settings is opened); otherwise ensureDay()'s
            // first-run reset would wipe the boost we're about to add.
            dayCountersStore.resetFor(todayStamp())

            // Request a wildly oversized boost, as reported (e.g. entering 222 with a
            // near-empty deck).
            dayCountersStore.addExtraNewToday(999, availableNew = database.vocabularyDao().countNewTotal())

            // The boost must be clamped: 1 permanent + boost can never exceed 4 unseen.
            val counters = dayCountersStore.read().first()
            val policy = dayCountersStore.readPolicy().first()
            assertThat(policy.newPerDay + counters.extraNewToday).isEqualTo(4)

            // And the deck can in fact only serve exactly 4 new cards today, not 999.
            reviewNextNewCard()
            reviewNextNewCard()
            reviewNextNewCard()
            reviewNextNewCard()
            assertThat(repository.hasAvailableItems()).isFalse()
        }

    @Test
    fun `add cards for today grants nothing when there are no unseen cards left`() =
        runTest {
            dayCountersStore.setNewPerDay(2)
            insertNewWords(2)
            reviewNextNewCard()
            reviewNextNewCard()
            assertThat(database.vocabularyDao().countNewTotal()).isEqualTo(0)

            dayCountersStore.addExtraNewToday(50, availableNew = database.vocabularyDao().countNewTotal())

            assertThat(dayCountersStore.read().first().extraNewToday).isEqualTo(0)
            assertThat(repository.hasAvailableItems()).isFalse()
        }
}
