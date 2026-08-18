package com.procrastilearn.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_words` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `word` TEXT COLLATE NOCASE NOT NULL,
                    `direction` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_words_word` ON `pending_words` (`word`)",
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `undo_snapshot` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `vocabId` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `snapshotDay` INTEGER NOT NULL,
                    `ratingName` TEXT NOT NULL,
                    `fsrsCardJson` TEXT NOT NULL,
                    `fsrsDueAt` INTEGER NOT NULL,
                    `lastShownAt` INTEGER,
                    `correctCount` INTEGER NOT NULL,
                    `incorrectCount` INTEGER NOT NULL,
                    `newShown` INTEGER NOT NULL,
                    `reviewShown` INTEGER NOT NULL,
                    `reviewsSinceLastNew` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_undo_snapshot_vocabId` ON `undo_snapshot` (`vocabId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_undo_snapshot_createdAt` ON `undo_snapshot` (`createdAt`)",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `bidirectional` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardFsrsCardJson` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardFsrsDueAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardCorrectCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardIncorrectCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardPromptOverride` TEXT")
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `backwardAnswerOverride` TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vocabulary_backwardFsrsDueAt` ON `vocabulary` (`backwardFsrsDueAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vocabulary_fsrsDueAt_backwardFsrsDueAt` " +
                    "ON `vocabulary` (`fsrsDueAt`, `backwardFsrsDueAt`)",
            )

            db.execSQL("ALTER TABLE `undo_snapshot` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'FORWARD'")
            db.execSQL("ALTER TABLE `undo_snapshot` ADD COLUMN `backwardFsrsCardJson` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `undo_snapshot` ADD COLUMN `backwardFsrsDueAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `undo_snapshot` ADD COLUMN `backwardCorrectCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `undo_snapshot` ADD COLUMN `backwardIncorrectCount` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `vocabulary` SET `position` = `id`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vocabulary_fsrsDueAt_backwardFsrsDueAt_position` " +
                    "ON `vocabulary` (`fsrsDueAt`, `backwardFsrsDueAt`, `position`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_vocabulary_position` ON `vocabulary` (`position`)",
            )
        }
    }

// Pure data-fix migration: closes any gaps in `position` left by deletions before this
// version (deletion now renumbers remaining rows on its own; this is a one-time catch-up for
// data that predates that fix). No column/index change, so schema-wise this is a no-op -
// still requires a version bump for Room's migration system to run it. Same two-phase
// negative-then-positive renumber the DAO uses, done procedurally (not a SQL window function)
// to avoid depending on any particular on-device SQLite version's feature set for a migration
// that only ever runs once, on a small table.
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val orderedIds = mutableListOf<Long>()
            db.query("SELECT id FROM vocabulary ORDER BY position ASC, id ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    orderedIds.add(cursor.getLong(0))
                }
            }
            orderedIds.forEachIndexed { index, id ->
                db.execSQL("UPDATE vocabulary SET position = ? WHERE id = ?", arrayOf(-(index + 1L), id))
            }
            orderedIds.forEachIndexed { index, id ->
                db.execSQL("UPDATE vocabulary SET position = ? WHERE id = ?", arrayOf(index + 1L, id))
            }
        }
    }
