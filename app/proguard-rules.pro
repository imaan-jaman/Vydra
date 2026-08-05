# Vydra ProGuard Rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.vydra.app.**$$serializer { *; }
-keepclassmembers class com.vydra.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.vydra.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }

# Jackson (used by youtubedl-android internally)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
}
-keep class com.yausername.youtubedl_android.mapper.** { *; }

# Apache Commons IO (used by youtubedl-android)
-dontwarn org.apache.commons.io.**
-keep class org.apache.commons.io.** { *; }
