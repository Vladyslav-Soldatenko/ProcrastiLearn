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
