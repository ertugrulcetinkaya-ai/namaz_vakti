# Gson populates these response DTOs reflectively. Keep only the DTO graph rather than
# the entire remote package; @SerializedName fixes the wire names independently of R8.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.PrayerTimesCalendarResponse { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.PrayerTimesData { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.PrayerMeta { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.PrayerDate { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.GregorianDate { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.HijriDate { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.HijriMonth { *; }
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.remote.PrayerTimings { *; }

# The legacy DataStore migration also deserializes this DTO reflectively.
-keep,allowoptimization,allowobfuscation class com.example.namazvakti.data.local.CachedPrayerDayDto { *; }

# Room discovers the entity and DAO metadata through generated code and annotations.
-keep class com.example.namazvakti.data.local.PrayerDayEntity { *; }

# WorkManager reconstructs workers from the class name stored in WorkRequest data.
-keep class com.example.namazvakti.widget.worker.PrayerWidgetWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}


# Room derives these generated implementation names reflectively in production.
-keep class com.example.namazvakti.data.local.PrayerTimesDatabase { *; }
-keep class com.example.namazvakti.data.local.PrayerTimesDatabase_Impl { *; }
-keep class com.example.namazvakti.data.local.PrayerDayDao { *; }
-keep class com.example.namazvakti.data.local.PrayerDayDao_Impl { *; }
