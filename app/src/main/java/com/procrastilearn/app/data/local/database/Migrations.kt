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

// SQLite's own LOWER()/UPPER() are ASCII-only in Android's bundled build (same blind spot as
// COLLATE NOCASE), so the case fold below must happen in Kotlin, not in SQL.
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `normalizedWord` TEXT NOT NULL DEFAULT ''")

            data class Candidate(
                val id: Long,
                val activity: Int,
            )

            val bestByNormalizedWord = mutableMapOf<String, Candidate>()
            val idsToDelete = mutableListOf<Long>()

            db
                .query(
                    "SELECT `id`, `word`, `correctCount`, `incorrectCount`, " +
                        "`backwardCorrectCount`, `backwardIncorrectCount` FROM `vocabulary`",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val word = cursor.getString(1)
                        val activity = cursor.getInt(2) + cursor.getInt(3) + cursor.getInt(4) + cursor.getInt(5)
                        val normalizedWord = word.trim().lowercase()

                        db.execSQL(
                            "UPDATE `vocabulary` SET `normalizedWord` = ? WHERE `id` = ?",
                            arrayOf<Any>(normalizedWord, id),
                        )

                        val current = bestByNormalizedWord[normalizedWord]
                        val newIsBetter =
                            current != null &&
                                (activity > current.activity || activity == current.activity && id < current.id)
                        when {
                            current == null -> {
                                bestByNormalizedWord[normalizedWord] = Candidate(id, activity)
                            }
                            newIsBetter -> {
                                idsToDelete.add(current.id)
                                bestByNormalizedWord[normalizedWord] = Candidate(id, activity)
                            }
                            else -> {
                                idsToDelete.add(id)
                            }
                        }
                    }
                }

            // Merge any rows this exact bug already produced (same word, non-ASCII case
            // difference) before the unique index below can be created - keep whichever row
            // has more review progress rather than discarding it arbitrarily.
            idsToDelete.forEach { id ->
                db.execSQL("DELETE FROM `vocabulary` WHERE `id` = ?", arrayOf(id))
            }

            db.execSQL("DROP INDEX IF EXISTS `index_vocabulary_word`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_vocabulary_normalizedWord` ON `vocabulary` (`normalizedWord`)",
            )
        }
    }
