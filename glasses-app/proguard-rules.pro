# ProGuard / R8 rules for the glasses app.
# Release minification is currently disabled (see build.gradle.kts); rules kept
# here so enabling it later is a one-line change.

# OkHttp / Okio (the Hermes HTTP/SSE client and the update downloader)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.botnick.rokidhermes.**$$serializer { *; }

# AppLauncher starts MainActivity by class name via Intent.setClassName, so keep
# it from being renamed/removed when minification is enabled.
-keep class com.botnick.rokidhermes.MainActivity { *; }
