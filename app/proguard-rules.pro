# ==============================================================================
# DiaNight ProGuard / R8 Rules
# Target: Maximum size reduction while preserving 100% compatibility with Android 4.4 (API 19)
# ==============================================================================

# 1. Application code: Keep all DiaNight classes, methods, and fields untouched
-keep class com.diaclock.nightstand.** { *; }
-keepclassmembers class com.diaclock.nightstand.** { *; }

# 2. Keep line numbers and file names for crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 3. Gson rules
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# 4. OkHttp 3.12.13 and Okio rules
# OkHttp uses reflection on Android platform internals (Conscrypt, OpenJSSE, etc.)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keepclassmembers class * extends okhttp3.internal.tls.OkHostnameVerifier { *; }

# 5. AndroidX & Material Design
-dontwarn androidx.**
-dontwarn com.google.android.material.**
-keepclassmembers class androidx.appcompat.widget.** { *; }
