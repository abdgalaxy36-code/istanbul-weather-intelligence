# R8 / ProGuard rules for the release build.
#
# The app has a very small reflective surface - Room entities, two
# kotlinx-serialization classes and the WorkManager worker. Everything else is
# plain Kotlin. The rules below are deliberately generous around that surface
# so minification can never break the database, the stored JSON payloads or
# the background worker.

# ---------------------------------------------------------------- Room
# Room's own consumer rules cover the generated implementations, but the
# entities are also read reflectively by the generated code, so keep them.
-keep class com.istanbul.weatherintel.data.db.** { *; }
-keep class com.istanbul.weatherintel.data.db.**_Impl { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------- kotlinx.serialization
# Keep the serializable classes and their generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The app's own serializable classes.
-keep class com.istanbul.weatherintel.data.repository.WeatherRepository$MeasurementJson { *; }
-keep class com.istanbul.weatherintel.ui.WeatherDataReader$StoredMeasurement { *; }
-keepclassmembers class com.istanbul.weatherintel.data.repository.WeatherRepository$MeasurementJson {
    *** Companion;
}
-keepclassmembers class com.istanbul.weatherintel.ui.WeatherDataReader$StoredMeasurement {
    *** Companion;
}

# --------------------------------------------------------------- Retrofit
# Retrofit's consumer rules cover this, but the interface methods are only
# referenced reflectively.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# -------------------------------------------------------------- OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ------------------------------------------------------------ WorkManager
# The worker is instantiated by WorkManager through its class name.
-keep class com.istanbul.weatherintel.worker.WeatherCollectionWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# --------------------------------------------------------------- Compose
# Compose ships its own consumer rules; keep the @Composable metadata that the
# compiler relies on.
-keepclassmembers class androidx.compose.runtime.** {
    <methods>;
}
-dontwarn androidx.compose.**

# ------------------------------------------------------------- DataStore
-keep class androidx.datastore.* { *; }
-dontwarn androidx.datastore.**

# ---------------------------------------------------------------- Misc
# Keep enum valueOf / values, used by the theme and data-type enums.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
