package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
    private lateinit var studyPreferences: StudyPreferencesDataStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        studyPreferences = StudyPreferencesDataStore(dataStoreContext)
        store = DayCountersStore(studyPreferences)
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
            assertThat(policy.studyDirectionMode).isEqualTo(StudyDirectionMode.BIDIRECTIONAL)
            assertThat(policy.ratingDelaySeconds).isEqualTo(0)
            assertThat(policy.newCardOrder).isEqualTo(NewCardOrder.SEQUENTIAL)
        }

    @Test
    fun readPolicyDefaultsToBidirectionalWhenOtherPreferencesExistButDirectionWasNeverChosen() =
        runTest {
            store.setMixMode(MixMode.NEW_FIRST)

            val policy = store.readPolicy().first()

            assertThat(policy.studyDirectionMode).isEqualTo(StudyDirectionMode.BIDIRECTIONAL)
        }

    @Test
    fun setStudyDirectionModePersistsAndIsReadBackCorrectly() =
        runTest {
            store.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)

            assertThat(store.readPolicy().first().studyDirectionMode).isEqualTo(StudyDirectionMode.BIDIRECTIONAL)

            store.setStudyDirectionMode(StudyDirectionMode.BACKWARD)

            assertThat(store.readPolicy().first().studyDirectionMode).isEqualTo(StudyDirectionMode.BACKWARD)
        }

    @Test
    fun readPolicyFallsBackToBidirectionalForACorruptedStudyDirectionModeString() =
        runTest {
            val key = stringPreferencesKey("study_direction_mode")
            studyPreferences.ds.edit { it[key] = "NOT_A_REAL_MODE" }

            val policy = store.readPolicy().first()

            assertThat(policy.studyDirectionMode).isEqualTo(StudyDirectionMode.BIDIRECTIONAL)
        }

    @Test
    fun counterMutationsUpdateValuesAsExpected() =
        runTest {
            store.resetFor(20_240_131)
            store.markReviewShown()
            store.markReviewShown()
            store.markNewShown()
            store.markReviewShown()

            val counters = store.read().first()
            assertThat(counters.yyyymmdd).isEqualTo(20_240_131)
            assertThat(counters.newShown).isEqualTo(1)
            assertThat(counters.reviewShown).isEqualTo(3)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(1)
            assertThat(counters.extraNewToday).isEqualTo(0)
        }

    @Test
    fun addExtraNewTodayAccumulatesAcrossCalls() =
        runTest {
            store.resetFor(20_240_131)
            store.addExtraNewToday(5, availableNew = 100)
            store.addExtraNewToday(3, availableNew = 100)

            val counters = store.read().first()
            assertThat(counters.extraNewToday).isEqualTo(8)
        }

    @Test
    fun addExtraNewTodayIgnoresZeroAndNegativeAmounts() =
        runTest {
            store.resetFor(20_240_131)
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
            store.resetFor(20_240_131)

            store.addExtraNewToday(50, availableNew = 20)

            assertThat(store.read().first().extraNewToday).isEqualTo(5)
        }

    @Test
    fun addExtraNewTodayAddsNothingWhenNoCapacityRemains() =
        runTest {
            // availableNew = 0: no unseen cards left, so no boost can be granted at all.
            store.resetFor(20_240_131)

            store.addExtraNewToday(10, availableNew = 0)

            assertThat(store.read().first().extraNewToday).isEqualTo(0)
        }

    @Test
    fun addExtraNewTodayRepeatedAddsStopAtCapacityInsteadOfAccumulatingPastIt() =
        runTest {
            store.resetFor(20_240_131)

            store.addExtraNewToday(3, availableNew = 20) // remaining 15 -> +3 = 18, capacity was 5
            store.addExtraNewToday(3, availableNew = 20) // remaining 18 -> capacity now 2, clamps to +2

            assertThat(store.read().first().extraNewToday).isEqualTo(5)
        }

    @Test
    fun addExtraNewTodayAccountsForNewShownWhenComputingCapacity() =
        runTest {
            // newPerDay=15, 10 already shown -> 5 remaining. 8 unseen cards left in the
            // deck means only 3 more can be granted before remaining would exceed unseen.
            store.resetFor(20_240_131)
            repeat(10) { store.markNewShown() }

            store.addExtraNewToday(100, availableNew = 8)

            assertThat(store.read().first().extraNewToday).isEqualTo(3)
        }

    @Test
    fun addExtraNewTodayAllowsFullAmountWhenWithinCapacity() =
        runTest {
            store.resetFor(20_240_131)

            store.addExtraNewToday(4, availableNew = 100)

            assertThat(store.read().first().extraNewToday).isEqualTo(4)
        }

    @Test
    fun resetForClearsExtraNewToday() =
        runTest {
            store.resetFor(20_240_131)
            store.addExtraNewToday(10, availableNew = 100)
            assertThat(store.read().first().extraNewToday).isEqualTo(10)

            store.resetFor(20_240_201)

            val counters = store.read().first()
            assertThat(counters.yyyymmdd).isEqualTo(20_240_201)
            assertThat(counters.extraNewToday).isEqualTo(0)
        }

    @Test
    fun restoreCountersSetsValuesAbsolutelyAndLeavesExtraNewTodayAndDayUntouched() =
        runTest {
            store.resetFor(20_240_131)
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
            assertThat(counters.yyyymmdd).isEqualTo(20_240_131)
        }

    @Test
    fun restoreCountersCanIncreaseValuesToo() =
        runTest {
            store.resetFor(20_240_131)

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

    @Test
    fun setRatingDelaySecondsRoundTrips() =
        runTest {
            store.setRatingDelaySeconds(5)

            assertThat(store.readPolicy().first().ratingDelaySeconds).isEqualTo(5)
        }

    @Test
    fun setRatingDelaySecondsDoesNotClampOutOfRangeValues() =
        runTest {
            // Range validation is the settings dialog's job (min/maxValue on the input),
            // not the store's -- it stores whatever it is given, including out-of-range input.
            store.setRatingDelaySeconds(-1)
            assertThat(store.readPolicy().first().ratingDelaySeconds).isEqualTo(-1)

            store.setRatingDelaySeconds(999)
            assertThat(store.readPolicy().first().ratingDelaySeconds).isEqualTo(999)
        }

    @Test
    fun setRatingDelaySecondsStoresBoundaryValuesVerbatim() =
        runTest {
            store.setRatingDelaySeconds(0)
            assertThat(store.readPolicy().first().ratingDelaySeconds).isEqualTo(0)

            store.setRatingDelaySeconds(60)
            assertThat(store.readPolicy().first().ratingDelaySeconds).isEqualTo(60)
        }

    @Test
    fun setRatingDelaySecondsLeavesOtherPolicyFieldsUntouched() =
        runTest {
            store.setNewPerDay(50)
            store.setReviewPerDay(60)
            store.setOverlayInterval(7)
            store.setMixMode(MixMode.NEW_FIRST)

            store.setRatingDelaySeconds(10)

            val policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(50)
            assertThat(policy.reviewPerDay).isEqualTo(60)
            assertThat(policy.overlayInterval).isEqualTo(7)
            assertThat(policy.mixMode).isEqualTo(MixMode.NEW_FIRST)
            assertThat(policy.ratingDelaySeconds).isEqualTo(10)
        }

    @Test
    fun setNewCardOrderRoundTrips() =
        runTest {
            store.setNewCardOrder(NewCardOrder.RANDOM)

            assertThat(store.readPolicy().first().newCardOrder).isEqualTo(NewCardOrder.RANDOM)

            store.setNewCardOrder(NewCardOrder.SEQUENTIAL)

            assertThat(store.readPolicy().first().newCardOrder).isEqualTo(NewCardOrder.SEQUENTIAL)
        }

    @Test
    fun readPolicyFallsBackToSequentialForACorruptedNewCardOrderString() =
        runTest {
            val key = stringPreferencesKey("new_card_order")
            studyPreferences.ds.edit { it[key] = "NOT_A_REAL_ORDER" }

            val policy = store.readPolicy().first()

            assertThat(policy.newCardOrder).isEqualTo(NewCardOrder.SEQUENTIAL)
        }

    @Test
    fun setNewCardOrderLeavesOtherPolicyFieldsUntouched() =
        runTest {
            store.setNewPerDay(50)
            store.setReviewPerDay(60)
            store.setMixMode(MixMode.NEW_FIRST)

            store.setNewCardOrder(NewCardOrder.RANDOM)

            val policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(50)
            assertThat(policy.reviewPerDay).isEqualTo(60)
            assertThat(policy.mixMode).isEqualTo(MixMode.NEW_FIRST)
            assertThat(policy.newCardOrder).isEqualTo(NewCardOrder.RANDOM)
        }
}
