package com.procrastilearn.app.data.local.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
// JVM/Robolectric counterpart to the instrumented AppDatabaseMigrationTest so JaCoCo counts this
// coverage; one test per migration/edge case for the same reason that file stays a single class.
@Suppress("LargeClass")
class RoomMigrationsTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migrate1To2CreatesPendingWordsTableAndKeepsVocabulary() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO vocabulary
                    (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt)
                VALUES ('Baum', 'Tree', 0, 1, 0, '', 100)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT word FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Baum", cursor.getString(0))
        }
        migrated.execSQL(
            "INSERT INTO pending_words (word, direction, createdAt) VALUES ('Neu', 'EN_TO_RU', 0)",
        )
        migrated.query("SELECT word, direction FROM pending_words").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Neu", cursor.getString(0))
            assertEquals("EN_TO_RU", cursor.getString(1))
        }
    }

    @Test
    fun migrate1To2EnforcesUniquePendingWord() {
        helper.createDatabase(TEST_DB, 1).apply { close() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.execSQL(
            "INSERT INTO pending_words (word, direction, createdAt) VALUES ('dup', 'EN_TO_RU', 0)",
        )

        var thrown: Throwable? = null
        try {
            migrated.execSQL(
                "INSERT INTO pending_words (word, direction, createdAt) VALUES ('dup', 'RU_TO_EN', 1)",
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    @Test
    fun migrate2To3CreatesUndoSnapshotTableWithNullableLastShownAt() {
        helper.createDatabase(TEST_DB, 2).apply { close() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        migrated.execSQL(
            """
            INSERT INTO undo_snapshot
                (vocabId, createdAt, snapshotDay, ratingName, fsrsCardJson, fsrsDueAt,
                 lastShownAt, correctCount, incorrectCount, newShown, reviewShown, reviewsSinceLastNew)
            VALUES (1, 0, 20260101, 'GOOD', '', 0, NULL, 0, 0, 0, 0, 0)
            """.trimIndent(),
        )
        migrated.query("SELECT ratingName, lastShownAt FROM undo_snapshot").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GOOD", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun migrate3To4DefaultsBackwardColumnsOnExistingRows() {
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

        val migrated =
            helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated
            .query(
                "SELECT bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, " +
                    "backwardCorrectCount, backwardIncorrectCount FROM vocabulary WHERE word = 'Katze'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
    }

    @Test
    fun migrate3To4AllowsInsertingFullyPopulatedBidirectionalRow() {
        helper.createDatabase(TEST_DB, 3).apply { close() }

        val migrated =
            helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        migrated.execSQL(
            """
            INSERT INTO vocabulary
                (word, translation, createdAt, correctCount, incorrectCount, fsrsCardJson, fsrsDueAt,
                 bidirectional, backwardFsrsCardJson, backwardFsrsDueAt,
                 backwardCorrectCount, backwardIncorrectCount)
            VALUES ('run', 'бігати', 0, 2, 0, 'fwd-json', 1000, 1, 'bwd-json', 2000, 4, 1)
            """.trimIndent(),
        )

        migrated
            .query(
                "SELECT bidirectional, backwardFsrsCardJson, backwardFsrsDueAt FROM vocabulary WHERE word = 'run'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("bwd-json", cursor.getString(1))
                assertEquals(2000, cursor.getLong(2))
            }
    }

    @Test
    fun migrate4To5BackfillsPositionFromId() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(insertV4Vocabulary("first"))
            execSQL(insertV4Vocabulary("second"))
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

        migrated.execSQL(insertV4VocabularyWithPosition("first", 7))

        var thrown: Throwable? = null
        try {
            migrated.execSQL(insertV4VocabularyWithPosition("second", 7))
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    @Test
    fun migrate5To6RenumbersGappedPositionsContiguouslyInAscendingOrder() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(insertV5VocabularyWithPosition("low", 5))
            execSQL(insertV5VocabularyWithPosition("mid", 12))
            execSQL(insertV5VocabularyWithPosition("high", 30))
            close()
        }

        val migrated = migrateTo6(migrated5Chain())

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
        }
    }

    @Test
    fun migrate5To6RenumbersReverseOrderedPositionsPreservingRelativeOrder() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(insertV5VocabularyWithPosition("zeta", 100))
            execSQL(insertV5VocabularyWithPosition("alpha", 1))
            execSQL(insertV5VocabularyWithPosition("mu", 50))
            close()
        }

        val migrated = migrateTo6(migrated5Chain())

        migrated.query("SELECT word FROM vocabulary ORDER BY position ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("alpha", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("mu", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("zeta", cursor.getString(0))
        }
    }

    @Test
    fun migrate5To6OnAnEmptyVocabularyTableIsANoOp() {
        helper.createDatabase(TEST_DB, 5).apply { close() }

        val migrated = migrateTo6(migrated5Chain())

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate5To6StillEnforcesUniquePositionAfterRenumbering() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(insertV5VocabularyWithPosition("first", 5))
            close()
        }

        val migrated = migrateTo6(migrated5Chain())

        var thrown: Throwable? = null
        try {
            migrated.execSQL(insertV4VocabularyWithPosition("second", 1))
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    @Test
    fun migrate6To7BackfillsNormalizedWordTrimmedAndLowercased() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(insertV5VocabularyWithPosition("  CAFÉ  ", 1))
            close()
        }

        val migrated = migrateTo7(migrated6Chain())

        migrated.query("SELECT word, normalizedWord FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("  CAFÉ  ", cursor.getString(0))
            assertEquals("café", cursor.getString(1))
        }
    }

    @Test
    fun migrate6To7MergesCaseDuplicatesKeepingTheRowWithMoreActivity() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(insertV6VocabularyWithActivity("café", "keep", correct = 5, incorrect = 2, position = 1))
            execSQL(insertV6VocabularyWithActivity("CAFÉ", "drop", correct = 0, incorrect = 0, position = 2))
            close()
        }

        val migrated = migrateTo7(migrated6Chain())

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT translation, correctCount FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("keep", cursor.getString(0))
            assertEquals(5, cursor.getInt(1))
        }
    }

    @Test
    fun migrate6To7BreaksActivityTiesByKeepingTheLowestId() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(insertV6VocabularyWithActivity("café", "first-inserted", correct = 0, incorrect = 0, position = 1))
            execSQL(insertV6VocabularyWithActivity("CAFÉ", "second-inserted", correct = 0, incorrect = 0, position = 2))
            close()
        }

        val migrated = migrateTo7(migrated6Chain())

        migrated.query("SELECT translation FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("first-inserted", cursor.getString(0))
        }
    }

    @Test
    fun migrate6To7PreservesRowsWithDistinctNormalizedWords() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(insertV6VocabularyWithActivity("Katze", "Cat", correct = 3, incorrect = 1, position = 1))
            execSQL(insertV6VocabularyWithActivity("Hund", "Dog", correct = 1, incorrect = 0, position = 2))
            close()
        }

        val migrated = migrateTo7(migrated6Chain())

        migrated.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrate6To7EnforcesUniqueNormalizedWordGoingForward() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(insertV5VocabularyWithPosition("café", 1))
            close()
        }

        val migrated = migrateTo7(migrated6Chain())

        var thrown: Throwable? = null
        try {
            migrated.execSQL(
                "INSERT INTO vocabulary (word, translation, createdAt, correctCount, incorrectCount, " +
                    "fsrsCardJson, fsrsDueAt, position) VALUES ('CAFÉ', 'dup', 0, 0, 0, '', 0, 2)",
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }

    private fun migrated5Chain(): Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

    private fun migrated6Chain(): Array<Migration> = migrated5Chain() + MIGRATION_6_7

    private fun migrateTo6(chain: Array<Migration>) = helper.runMigrationsAndValidate(TEST_DB, 6, true, *chain)

    private fun migrateTo7(chain: Array<Migration>) = helper.runMigrationsAndValidate(TEST_DB, 7, true, *chain)

    private fun insertV4Vocabulary(word: String) =
        "INSERT INTO vocabulary (word, translation, createdAt, correctCount, incorrectCount, " +
            "fsrsCardJson, fsrsDueAt, bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, " +
            "backwardCorrectCount, backwardIncorrectCount) " +
            "VALUES ('$word', 'x', 0, 0, 0, '', 0, 0, '', 0, 0, 0)"

    private fun insertV4VocabularyWithPosition(
        word: String,
        position: Int,
    ) = "INSERT INTO vocabulary (word, translation, createdAt, correctCount, incorrectCount, " +
        "fsrsCardJson, fsrsDueAt, bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, " +
        "backwardCorrectCount, backwardIncorrectCount, position) " +
        "VALUES ('$word', 'x', 0, 0, 0, '', 0, 0, '', 0, 0, 0, $position)"

    private fun insertV5VocabularyWithPosition(
        word: String,
        position: Int,
    ) = insertV4VocabularyWithPosition(word, position)

    private fun insertV6VocabularyWithActivity(
        word: String,
        translation: String,
        correct: Int,
        incorrect: Int,
        position: Int,
    ) = "INSERT INTO vocabulary (word, translation, createdAt, correctCount, incorrectCount, " +
        "fsrsCardJson, fsrsDueAt, bidirectional, backwardFsrsCardJson, backwardFsrsDueAt, " +
        "backwardCorrectCount, backwardIncorrectCount, position) " +
        "VALUES ('$word', '$translation', 0, $correct, $incorrect, '', 0, 0, '', 0, 0, 0, $position)"

    private companion object {
        const val TEST_DB = "room-migrations-test"
    }
}
