# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room database classes
-keep class com.odinu.forwardsms.data.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep Retrofit and Gson classes
-keep class com.odinu.forwardsms.network.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep BroadcastReceiver
-keep class com.odinu.forwardsms.SmsReceiver { *; }
-keep class com.odinu.forwardsms.MessageNotificationListener { *; }

# Keep Service classes
-keep class com.odinu.forwardsms.service.** { *; }

# Keep ViewModel classes
-keep class com.odinu.forwardsms.ui.FilterViewModel { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Android components
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# Keep custom exceptions
-keep class com.odinu.forwardsms.utils.** { *; }