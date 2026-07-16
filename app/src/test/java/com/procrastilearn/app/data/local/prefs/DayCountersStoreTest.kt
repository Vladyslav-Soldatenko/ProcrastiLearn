package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.domain.model.MixMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DayCountersStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: DayCountersStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        store = DayCountersStore(StudyPreferencesDataStore(dataStoreContext))
    }

    @Test
    fun readFlowsEmitDefaultsWhenPreferencesMissing() =
        runTest {
            val counters = store.read().first()
            assertThat(counters).isEqualTo(DayCounters(0, 0, 0, 0, 0))

            val policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(15)
            assertThat(policy.reviewPerDay).isEqualTo(99)
            assertThat(policy.overlayInterval).isEqualTo(0)
            assertThat(policy.mixMode).isEqualTo(MixMode.MIX)
        }

    @Test
    fun counterMutationsUpdateValuesAsExpected() =
        runTest {
            store.resetFor(20240131)
            store.markReviewShown()
            store.markReviewShown()
            store.markNewShown()
            store.markReviewShown()

            val counters = store.read().first()
            assertThat(counters.yyyymmdd).isEqualTo(20240131)
            assertThat(counters.newShown).isEqualTo(1)
            assertThat(counters.reviewShown).isEqualTo(3)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(1)
            assertThat(counters.extraNewToday).isEqualTo(0)
        }

    @Test
    fun addExtraNewTodayAccumulatesAcrossCalls() =
        runTest {
            store.resetFor(20240131)
            store.addExtraNewToday(5, availableNew = 100)
            store.addExtraNewToday(3, availableNew = 100)

            val counters = store.read().first()
            assertThat(counters.extraNewToday).isEqualTo(8)
        }

    @Test
    fun addExtraNewTodayIgnoresZeroAndNegativeAmounts() =
        runTest {
            store.resetFor(20240131)
            store.addExtraNewToday(10, availableNew = 100)
            store.addExtraNewToday(0, availableNew = 100)
            store.addExtraNewToday(-5, availableNew = 100)

            val counters = store.read().first()
            assertThat(counters.extraNewToday).isEqualTo(10)
        }

    @Test
    fun addExtraNewTodayClampsToAvailableNewWhenAmountExceedsCapacity() =
        runTest {
            // newPerDay defaults to 15, nothing shown yet -> 15 already "remaining".
            // Only 20 cards are unseen in the deck, so at most 5 more can be added.
            store.resetFor(20240131)

            store.addExtraNewToday(50, availableNew = 20)

            assertThat(store.read().first().extraNewToday).isEqualTo(5)
        }

    @Test
    fun addExtraNewTodayAddsNothingWhenNoCapacityRemains() =
        runTest {
            // availableNew = 0: no unseen cards left, so no boost can be granted at all.
            store.resetFor(20240131)

            store.addExtraNewToday(10, availableNew = 0)

            assertThat(store.read().first().extraNewToday).isEqualTo(0)
        }

    @Test
    fun addExtraNewTodayRepeatedAddsStopAtCapacityInsteadOfAccumulatingPastIt() =
        runTest {
            store.resetFor(20240131)

            store.addExtraNewToday(3, availableNew = 20) // remaining 15 -> +3 = 18, capacity was 5
            store.addExtraNewToday(3, availableNew = 20) // remaining 18 -> capacity now 2, clamps to +2

            assertThat(store.read().first().extraNewToday).isEqualTo(5)
        }

    @Test
    fun addExtraNewTodayAccountsForNewShownWhenComputingCapacity() =
        runTest {
            // newPerDay=15, 10 already shown -> 5 remaining. 8 unseen cards left in the
            // deck means only 3 more can be granted before remaining would exceed unseen.
            store.resetFor(20240131)
            repeat(10) { store.markNewShown() }

            store.addExtraNewToday(100, availableNew = 8)

            assertThat(store.read().first().extraNewToday).isEqualTo(3)
        }

    @Test
    fun addExtraNewTodayAllowsFullAmountWhenWithinCapacity() =
        runTest {
            store.resetFor(20240131)

            store.addExtraNewToday(4, availableNew = 100)

            assertThat(store.read().first().extraNewToday).isEqualTo(4)
        }

    @Test
    fun resetForClearsExtraNewToday() =
        runTest {
            store.resetFor(20240131)
            store.addExtraNewToday(10, availableNew = 100)
            assertThat(store.read().first().extraNewToday).isEqualTo(10)

            store.resetFor(20240201)

            val counters = store.read().first()
            assertThat(counters.yyyymmdd).isEqualTo(20240201)
            assertThat(counters.extraNewToday).isEqualTo(0)
        }

    @Test
    fun restoreCountersSetsValuesAbsolutelyAndLeavesExtraNewTodayAndDayUntouched() =
        runTest {
            store.resetFor(20240131)
            store.addExtraNewToday(7, availableNew = 100)
            store.markReviewShown()
            store.markReviewShown()
            store.markNewShown()

            store.restoreCounters(newShown = 0, reviewShown = 0, reviewsSinceLastNew = 0)

            val counters = store.read().first()
            assertThat(counters.newShown).isEqualTo(0)
            assertThat(counters.reviewShown).isEqualTo(0)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(0)
            // Untouched by restore:
            assertThat(counters.extraNewToday).isEqualTo(7)
            assertThat(counters.yyyymmdd).isEqualTo(20240131)
        }

    @Test
    fun restoreCountersCanIncreaseValuesToo() =
        runTest {
            store.resetFor(20240131)

            store.restoreCounters(newShown = 4, reviewShown = 9, reviewsSinceLastNew = 2)

            val counters = store.read().first()
            assertThat(counters.newShown).isEqualTo(4)
            assertThat(counters.reviewShown).isEqualTo(9)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(2)
        }

    @Test
    fun policyLimitsAreClampedAndMixModePersists() =
        runTest {
            store.setMixMode(MixMode.NEW_FIRST)
            store.setNewPerDay(500)
            store.setReviewPerDay(5000)
            store.setOverlayInterval(5000)

            var policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(200)
            assertThat(policy.reviewPerDay).isEqualTo(2000)
            assertThat(policy.overlayInterval).isEqualTo(2000)
            assertThat(policy.mixMode).isEqualTo(MixMode.NEW_FIRST)

            store.setNewPerDay(-5)
            store.setReviewPerDay(-10)
            store.setOverlayInterval(-1)

            policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(0)
            assertThat(policy.reviewPerDay).isEqualTo(0)
            assertThat(policy.overlayInterval).isEqualTo(0)
        }
}
