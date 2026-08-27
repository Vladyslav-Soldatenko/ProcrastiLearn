# Lombok annotations referenced by FSRS library; not needed at runtime
-dontwarn lombok.**

-keep class com.openai.core.** { *; }
-keep class com.openai.errors.** { *; }
-keep class com.openai.models.ChatModel { *; }
-keep class com.openai.models.ReasoningEffort { *; }
-keep class com.openai.models.chat.** { *; }

-dontwarn com.fasterxml.jackson.databind.**

-keep class io.github.openspacedrepetition.Card { *; }
-keep class io.github.openspacedrepetition.Card$Builder { *; }

# Unused optional openai-java code paths (multipart file uploads, JSON-schema
# generation) referencing classes we don't have on the classpath
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
-dontwarn org.apache.hc.core5.http.ContentType
-dontwarn org.apache.hc.core5.http.HttpEntity
