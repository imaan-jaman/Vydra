# Vydra ProGuard Rules

# Keep everything — app is small enough, no need to strip aggressively
-keep class com.vydra.app.** { *; }
-keep class com.vydra.app.**$$HiltModules* { *; }
-keep class com.vydra.app.**_MembersInjector { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
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
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class **_HiltModules* { *; }
-keep class **_HiltComponents* { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-keep class **_Impl { *; }
-keep class dagger.hilt.android.internal.** { *; }
-keep class dagger.hilt.internal.** { *; }

# WorkManager + Hilt Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }

# Compose
-dontwarn androidx.compose.**

# Activities and Fragments
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

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

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Prevent R8 from stripping interface information
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
