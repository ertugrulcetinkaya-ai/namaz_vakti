# The minified managed-device variant runs the instrumentation APK against the
# obfuscated app. Keep test-framework classes and app APIs that the test APK
# reaches directly through reflection or Kotlin synthetic default constructors.
-keep class androidx.tracing.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.work.** { *; }
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

-keep class com.example.namazvakti.domain.model.** { *; }
-keep class com.example.namazvakti.data.local.CachedPrayerDayCodec { *; }
-keep class com.example.namazvakti.data.local.PrayerTimesDatabase { *; }
-keep class com.example.namazvakti.data.local.PrayerTimesStore { *; }
-keep class com.example.namazvakti.data.local.PrayerDayDao { *; }
-keep class com.example.namazvakti.data.local.PrayerTimesDatabaseKt { *; }
-keep class com.example.namazvakti.data.local.PrayerTimesStoreKt { *; }
-keep class com.example.namazvakti.widget.PrayerWidgetSnapshot { *; }
-keep class com.example.namazvakti.widget.renderer.** { *; }
-keep class com.example.namazvakti.widget.worker.** { *; }
-keep class com.example.namazvakti.R { *; }
-keep class com.example.namazvakti.R$* { *; }
