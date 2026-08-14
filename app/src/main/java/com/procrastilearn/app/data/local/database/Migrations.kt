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
        }
    }
