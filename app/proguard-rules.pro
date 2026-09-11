# Gson populates the API DTOs reflectively.
-keep class com.example.namazvakti.data.remote.** { *; }

# Room discovers the entity and DAO metadata through generated code and annotations.
-keep class com.example.namazvakti.data.local.PrayerDayEntity { *; }

# WorkManager reconstructs workers from the class name stored in WorkRequest data.
-keep class com.example.namazvakti.widget.worker.PrayerWidgetWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
