# Gson 2.14 ships consumer R8 rules for fields annotated with @SerializedName.
# Keep DTOs optimizable; whole-class Gson keep rules would retain unused members.

# WorkManager reconstructs workers from the class name stored in WorkRequest data.
-keep class com.example.namazvakti.widget.worker.PrayerWidgetWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
