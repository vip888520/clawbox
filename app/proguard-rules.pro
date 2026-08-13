# Keep rules for ClawBox release builds

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization (dynamic JsonElement usage; keep serializer machinery)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# Apache Commons Compress (runtime unpacking)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# ClawBox app classes (keep entry points)
-keep class com.lobster.clawbox.** { *; }
