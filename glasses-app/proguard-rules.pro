# ProGuard / R8 rules for the glasses app.
# Release minification is currently disabled (see build.gradle.kts); rules kept
# here so enabling it later is a one-line change.

# OkHttp / Okio (used by the loader's downloader and the gateway client)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.botnick.rokidhermes.**$$serializer { *; }

# DynamicLoader loads MainActivity reflectively from a DEX bundle — keep it.
-keep class com.botnick.rokidhermes.MainActivity { *; }
