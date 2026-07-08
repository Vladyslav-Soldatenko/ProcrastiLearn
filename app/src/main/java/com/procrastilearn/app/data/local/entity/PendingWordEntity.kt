package com.procrastilearn.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_words",
    indices = [Index(value = ["word"], unique = true)],
)
data class PendingWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    val direction: String,
    val createdAt: Long = System.currentTimeMillis(),
)
