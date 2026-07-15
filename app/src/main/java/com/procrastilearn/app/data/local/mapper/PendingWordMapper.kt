package com.procrastilearn.app.data.local.mapper

import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.PendingWordStatus

fun PendingWordEntity.toDomain(): PendingWord =
    PendingWord(
        id = id,
        word = word,
        direction =
            runCatching { AiTranslationDirection.valueOf(direction) }
                .getOrDefault(AiTranslationDirection.EN_TO_RU),
        createdAt = createdAt,
        status =
            runCatching { PendingWordStatus.valueOf(status) }
                .getOrDefault(PendingWordStatus.PENDING),
        retryCount = retryCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
    )

fun PendingWord.toEntity(): PendingWordEntity =
    PendingWordEntity(
        id = id,
        word = word,
        direction = direction.name,
        createdAt = createdAt,
        status = status.name,
        retryCount = retryCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
    )
