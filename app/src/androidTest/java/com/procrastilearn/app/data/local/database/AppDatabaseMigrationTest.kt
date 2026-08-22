package com.procrastilearn.app.data.local.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// One independent test per historical schema migration and edge case; splitting would scatter
// that migration history across files for no benefit.
@Suppress("LargeClass")
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

    @Test
    fun migrate4To5AddsPositionColumnBackfilledFromId() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount)
                VALUES ('first', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount)
                VALUES ('second', 'b', 0, 0, 0, '', 0, 0, '', 0, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount)
                VALUES ('third', 'c', 0, 0, 0, '', 0, 0, '', 0, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )

        migrated.query("SELECT word, id, position FROM vocabulary ORDER BY id ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("first", cursor.getString(0))
            assertEquals(cursor.getLong(1), cursor.getLong(2))
            assertTrue(cursor.moveToNext())
            assertEquals("second", cursor.getString(0))
            assertEquals(cursor.getLong(1), cursor.getLong(2))
            assertTrue(cursor.moveToNext())
            assertEquals("third", cursor.getString(0))
            assertEquals(cursor.getLong(1), cursor.getLong(2))
            assertTrue(cursor.isLast)
        }
    }

    @Test
    fun migrate4To5DefaultsPositionToZeroWhenInsertedWithoutIt() {
        helper.createDatabase(TEST_DB, 4).apply { close() }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount)
            VALUES ('newword', 'x', 0, 0, 0, '', 0, 0, '', 0, 0, 0)
            """.trimIndent(),
        )
        migrated.query("SELECT position FROM vocabulary WHERE word = 'newword'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getLong(0))
        }
    }

    @Test
    fun migrate4To5AllowsInsertingExplicitPositionAndQueryingByIt() {
        helper.createDatabase(TEST_DB, 4).apply { close() }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount, position)
            VALUES ('later', 'y', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 500)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount, position)
            VALUES ('sooner', 'z', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 10)
            """.trimIndent(),
        )

        migrated
            .query(
                """
                SELECT word FROM vocabulary
                WHERE fsrsDueAt = 0 AND backwardFsrsDueAt = 0
                ORDER BY position ASC, id ASC
                LIMIT 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("sooner", cursor.getString(0))
            }
    }

    @Test
    fun migrate4To5EnforcesUniquePosition() {
        helper.createDatabase(TEST_DB, 4).apply { close() }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount, position)
            VALUES ('first', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 7)
            """.trimIndent(),
        )

        var thrown: Throwable? = null
        try {
            migrated.execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount, backwardIncorrectCount, position)
                VALUES ('second', 'b', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 7)
                """.trimIndent(),
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    @Test
    fun migrate5To6RenumbersGappedLegacyPositionsContiguously() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('low', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 5)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('mid', 'b', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 12)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('high', 'c', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 30)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        migrated.query("SELECT word, position FROM vocabulary ORDER BY position ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("low", cursor.getString(0))
            assertEquals(1L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals("mid", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals("high", cursor.getString(0))
            assertEquals(3L, cursor.getLong(1))
            assertTrue(cursor.isLast)
        }
    }

    @Test
    fun migrate5To6LeavesAlreadyContiguousPositionsUnchanged() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('first', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('second', 'b', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 2)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        migrated.query("SELECT position FROM vocabulary WHERE word = 'first'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        migrated.query("SELECT position FROM vocabulary WHERE word = 'second'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2L, cursor.getLong(0))
        }
    }

    @Test
    fun migrate5To6OnAnEmptyVocabularyTableIsANoOp() {
        helper.createDatabase(TEST_DB, 5).apply { close() }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate5To6PreservesRelativeOrderWhenRenumbering() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('zeta', 'z', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 100)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('alpha', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('mu', 'm', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 50)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        migrated.query("SELECT word FROM vocabulary ORDER BY position ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("alpha", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("mu", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("zeta", cursor.getString(0))
            assertTrue(cursor.isLast)
        }
    }

    @Test
    fun migrate5To6PreservesAllNonPositionColumns() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('run', 'бігати', 0, 2, 1, 'fwd-json', 1000, 1, 'bwd-json', 2000, 4, 1, 999)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        migrated
            .query(
                """
                SELECT translation, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                       bidirectional, backwardFsrsCardJson, backwardFsrsDueAt,
                       backwardCorrectCount, backwardIncorrectCount, position
                FROM vocabulary WHERE word = 'run'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("бігати", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("fwd-json", cursor.getString(3))
                assertEquals(1000, cursor.getLong(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals("bwd-json", cursor.getString(6))
                assertEquals(2000, cursor.getLong(7))
                assertEquals(4, cursor.getInt(8))
                assertEquals(1, cursor.getInt(9))
                assertEquals(1L, cursor.getLong(10))
            }
    }

    @Test
    fun migrate5To6StillEnforcesUniquePositionAfterMigration() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('first', 'a', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 5)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                6,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )

        var thrown: Throwable? = null
        try {
            migrated.execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('second', 'b', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    @Test
    fun migrate6To7BackfillsNormalizedWordFromExistingRows() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('CAFÉ', 'coffee', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

        migrated.query("SELECT word, normalizedWord FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CAFÉ", cursor.getString(0))
            assertEquals("café", cursor.getString(1))
        }
    }

    @Test
    fun migrate6To7MergesPreexistingNonAsciiCaseDuplicatesKeepingRowWithMoreProgress() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('café', 'coffee', 0, 5, 2, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('CAFÉ', 'coffee (dup)', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 2)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT word, correctCount FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("café", cursor.getString(0))
            assertEquals(5, cursor.getInt(1))
        }
    }

    @Test
    fun migrate6To7TieBreaksKeptDuplicateByLowestId() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('café', 'first', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('CAFÉ', 'second', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 2)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

        migrated.query("SELECT translation FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("first", cursor.getString(0))
            assertTrue(cursor.isLast)
        }
    }

    @Test
    fun migrate6To7EnforcesUniqueNormalizedWordGoingForward() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('café', 'coffee', 0, 0, 0, '', 0, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

        var threw = false
        try {
            migrated.execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt, position)
                VALUES ('CAFÉ', 'coffee (dup)', 0, 0, 0, '', 0, 2)
                """.trimIndent(),
            )
        } catch (expected: SQLiteConstraintException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun migrate6To7PreservesRowsWithDistinctNormalizedWords() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('Katze', 'Cat', 0, 3, 1, 'card-json', 500, 0, '', 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                     bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, backwardCorrectCount,
                     backwardIncorrectCount, position)
                VALUES ('Hund', 'Dog', 0, 1, 0, '', 0, 0, '', 0, 0, 0, 2)
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
