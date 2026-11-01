# Lombok annotations referenced by FSRS library; not needed at runtime
-dontwarn lombok.**

-dontwarn com.fasterxml.jackson.databind.**

-keep class io.github.openspacedrepetition.Card { *; }
-keep class io.github.openspacedrepetition.Card$Builder { *; }

-keepclassmembers class com.github.luben.zstd.ZstdInputStreamNoFinalizer {
    long srcPos;
    long dstPos;
}
