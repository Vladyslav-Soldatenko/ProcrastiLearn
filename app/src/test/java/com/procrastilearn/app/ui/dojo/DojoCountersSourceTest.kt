package com.procrastilearn.app.ui.dojo

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirectionMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DojoCountersSourceTest {
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>

    private val baseNow = 1_700_000_000_000L
    private lateinit var nowTicker: MutableStateFlow<Long>
    private var liveNow = baseNow
    private val fakeTimeTicker =
        object : TimeTicker {
            override fun nowTicks(): Flow<Long> = nowTicker

            override fun now(): Long = liveNow
        }

    @Before
    fun setUp() {
        vocabularyDao = mockk()
        dayCountersStore = mockk()
        policyFlow =
            MutableStateFlow(
                LearningPreferencesConfig(
                    newPerDay = 20,
                    reviewPerDay = 100,
                    mixMode = MixMode.MIX,
                    overlayInterval = 6,
                ),
            )
        nowTicker = MutableStateFlow(baseNow)
        liveNow = baseNow

        every { dayCountersStore.readPolicy() } returns policyFlow
    }

    private fun buildSource(): DojoCountersSource = DojoCountersSource(vocabularyDao, dayCountersStore, fakeTimeTicker)

    @Test
    fun `reviewsDueAndSkippedCount reports zero skipped when not in backward-only mode`() =
        runTest {
            every { vocabularyDao.observeReviewsDueCount(any(), true, false) } returns MutableStateFlow(7)
            val source = buildSource()

            source.reviewsDueAndSkippedCount.test {
                assertThat(awaitItem()).isEqualTo(7 to 0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reviewsDueAndSkippedCount includes the DAO's skip count only in backward-only mode`() =
        runTest {
            policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BACKWARD)
            every { vocabularyDao.observeReviewsDueCount(any(), false, true) } returns MutableStateFlow(3)
            every { vocabularyDao.observeBackwardOnlySkippedCount(any()) } returns MutableStateFlow(2)
            val source = buildSource()

            source.reviewsDueAndSkippedCount.test {
                assertThat(awaitItem()).isEqualTo(3 to 2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reviewsDueAndSkippedCount re-queries the DAO when now ticks forward`() =
        runTest {
            val dueDelayMs = 2 * 60_000L
            every { vocabularyDao.observeReviewsDueCount(any(), true, false) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + dueDelayMs) 5 else 0)
            }
            val source = buildSource()

            source.reviewsDueAndSkippedCount.test {
                assertThat(awaitItem()).isEqualTo(0 to 0)

                nowTicker.value = baseNow + dueDelayMs

                assertThat(awaitItem()).isEqualTo(5 to 0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh re-queries the DAO with a live now instead of waiting for the next tick`() =
        runTest {
            val dueDelayMs = 2 * 60_000L
            every { vocabularyDao.observeReviewsDueCount(any(), true, false) } answers {
                val now = firstArg<Long>()
                flowOf(if (now >= baseNow + dueDelayMs) 5 else 0)
            }
            val source = buildSource()

            source.reviewsDueAndSkippedCount.test {
                assertThat(awaitItem()).isEqualTo(0 to 0)

                liveNow = baseNow + dueDelayMs
                assertThat(nowTicker.value).isEqualTo(baseNow)
                source.refresh()

                assertThat(awaitItem()).isEqualTo(5 to 0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `newTotalCount re-queries when studyDirectionMode's backward-only-ness changes`() =
        runTest {
            val forwardNewFlow = MutableStateFlow(20)
            val backwardNewFlow = MutableStateFlow(4)
            every { vocabularyDao.observeNewTotalCount(false) } returns forwardNewFlow
            every { vocabularyDao.observeNewTotalCount(true) } returns backwardNewFlow
            val source = buildSource()

            source.newTotalCount.test {
                assertThat(awaitItem()).isEqualTo(20)

                policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BACKWARD)

                assertThat(awaitItem()).isEqualTo(4)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
