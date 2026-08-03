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

    @Test
    fun migrate3To4AddsBidirectionalColumnsAndKeepsExistingVocabulary() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt)
                VALUES ('Katze', 'Cat', 0, 3, 1, 'card-json', 500)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated
            .query(
                "SELECT word, translation, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt FROM vocabulary",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Katze", cursor.getString(0))
                assertEquals("Cat", cursor.getString(1))
                assertEquals(3, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals("card-json", cursor.getString(4))
                assertEquals(500, cursor.getLong(5))
            }

        migrated
            .query(
                """
                SELECT bidirectional, backwardFsrsCardJson, backwardFsrsDueAt,
                       backwardCorrectCount, backwardIncorrectCount, backwardPromptOverride, backwardAnswerOverride
                FROM vocabulary
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
            }
    }

    @Test
    fun migrate3To4AllowsInsertingFullyPopulatedBidirectionalRow() {
        helper.createDatabase(TEST_DB, 3).apply { close() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt,
                 backwardCorrectCount, backwardIncorrectCount, backwardPromptOverride, backwardAnswerOverride)
            VALUES ('run', 'бігати', 0, 2, 0, 'fwd-json', 1000,
                    1, 'bwd-json', 2000, 4, 1, 'custom prompt', 'custom answer')
            """.trimIndent(),
        )

        migrated
            .query(
                """
                SELECT bidirectional, backwardFsrsCardJson, backwardFsrsDueAt,
                       backwardCorrectCount, backwardIncorrectCount, backwardPromptOverride, backwardAnswerOverride
                FROM vocabulary WHERE word = 'run'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("bwd-json", cursor.getString(1))
                assertEquals(2000, cursor.getLong(2))
                assertEquals(4, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals("custom prompt", cursor.getString(5))
                assertEquals("custom answer", cursor.getString(6))
            }
    }

    @Test
    fun migrate3To4QueryableByBackwardDueAt() {
        helper.createDatabase(TEST_DB, 3).apply { close() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsDueAt)
            VALUES ('due-word', 'x', 0, 0, 0, '', 0, 1, 100)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsDueAt)
            VALUES ('not-due-word', 'y', 0, 0, 0, '', 0, 0, 0)
            """.trimIndent(),
        )

        migrated.query("SELECT word FROM vocabulary WHERE backwardFsrsDueAt > 0").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("due-word", cursor.getString(0))
            assertTrue(cursor.isLast)
        }
    }

    @Test
    fun migrate3To4AddsUndoSnapshotBackwardColumns() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt)
                VALUES ('Vogel', 'Bird', 0, 1, 0, '', 100)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO undo_snapshot
                    (vocabId, createdAt, snapshotDay, ratingName, fsrsCardJson, fsrsDueAt,
                     lastShownAt, correctCount, incorrectCount, newShown, reviewShown, reviewsSinceLastNew)
                VALUES (1, 0, 20260117, 'GOOD', '', 0, NULL, 0, 0, 0, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated
            .query(
                "SELECT direction, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, " +
                    "backwardIncorrectCount FROM undo_snapshot",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FORWARD", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
