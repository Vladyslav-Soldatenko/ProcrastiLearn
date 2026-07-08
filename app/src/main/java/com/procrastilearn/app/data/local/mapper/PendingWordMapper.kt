package com.procrastilearn.app.data.local.mapper

import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord

fun PendingWordEntity.toDomain(): PendingWord =
    PendingWord(
        id = id,
        word = word,
        direction =
            runCatching { AiTranslationDirection.valueOf(direction) }
                .getOrDefault(AiTranslationDirection.EN_TO_RU),
        createdAt = createdAt,
    )

fun PendingWord.toEntity(): PendingWordEntity =
    PendingWordEntity(
        id = id,
        word = word,
        direction = direction.name,
        createdAt = createdAt,
    )
