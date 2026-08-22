package com.procrastilearn.app.data.export

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.parser.anki.AnkiApkgVocabularyParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AnkiImportOrderIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: VocabularyDao
    private lateinit var manager: VocabularyTransferManager

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.vocabularyDao()
        manager =
            VocabularyTransferManager(
                vocabularyDao = dao,
                parsers = setOf(AnkiApkgVocabularyParser()),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `importing the ordered deck saves new cards with position matching the parsed order`() =
        runTest {
            val deckFile = loadResource("import/anki/English-German_Ordered_Deck.apkg")
            val expectedOrder = AnkiApkgVocabularyParser().parse(deckFile).map { it.word }

            val result = manager.importFromUri(RuntimeEnvironment.getApplication(), "apkg", Uri.fromFile(deckFile))

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = expectedOrder.size))
            val savedInOrder =
                dao
                    .getAllVocabulary()
                    .first()
                    .sortedBy { it.position }
                    .map { it.word }
            assertThat(savedInOrder).isEqualTo(expectedOrder)
        }

    private fun loadResource(path: String): File {
        val url =
            checkNotNull(javaClass.classLoader?.getResource(path)) {
                "Resource at $path was not found in the test resources."
            }
        return File(url.toURI())
    }
}
