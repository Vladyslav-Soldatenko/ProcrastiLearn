package com.procrastilearn.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migrate1To2AddsThePendingWordsTableAndKeepsExistingVocabulary() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt)
                VALUES ('Haus', 'House', 0, 0, 0, '', 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT word FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Haus", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM pending_words").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.execSQL(
            "INSERT INTO pending_words (word, direction, createdAt) VALUES ('Auto', 'EN_TO_RU', 0)",
        )
        migrated.query("SELECT word, direction FROM pending_words").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Auto", cursor.getString(0))
            assertEquals("EN_TO_RU", cursor.getString(1))
        }
    }

    @Test
    fun migrate2To3AddsTheUndoSnapshotTableAndKeepsExistingVocabulary() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt)
                VALUES ('Baum', 'Tree', 0, 1, 0, '', 100)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        migrated.query("SELECT word FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Baum", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM undo_snapshot").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.execSQL(
            """
            INSERT INTO undo_snapshot
                (vocabId, createdAt, snapshotDay, ratingName, fsrsCardJson, fsrsDueAt,
                 lastShownAt, correctCount, incorrectCount, newShown, reviewShown, reviewsSinceLastNew)
            VALUES (1, 0, 20260117, 'GOOD', '', 0, NULL, 0, 0, 0, 0, 0)
            """.trimIndent(),
        )
        migrated.query("SELECT ratingName, lastShownAt FROM undo_snapshot").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GOOD", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
