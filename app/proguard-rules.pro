# Lombok annotations referenced by FSRS library; not needed at runtime
-dontwarn lombok.**
-keepattributes *Annotation*

# openai-java SDK relies on Jackson reflection over its request/response POJOs
# (e.g. JsonMissing sentinel serialization) which R8 breaks without these keeps
-keep class com.openai.** { *; }
-keepnames class com.openai.** { *; }
-keepclassmembers,allowobfuscation class com.openai.** { *; }

-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

-keepattributes Signature, InnerClasses, EnclosingMethod

# FSRS scheduling library (io.github.open-spaced-repetition:fsrs) serializes
# Card via Jackson builder reflection (Card.toJson/fromJson) and ships no
# consumer proguard rules of its own, so R8 strips the getters/builder methods
# Jackson needs, breaking review saves silently in release
-keep class io.github.openspacedrepetition.** { *; }
-keepnames class io.github.openspacedrepetition.** { *; }
-keepclassmembers,allowobfuscation class io.github.openspacedrepetition.** { *; }

# Unused optional openai-java code paths (multipart file uploads, JSON-schema
# generation) referencing classes we don't have on the classpath
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
-dontwarn org.apache.hc.core5.http.ContentType
-dontwarn org.apache.hc.core5.http.HttpEntity
