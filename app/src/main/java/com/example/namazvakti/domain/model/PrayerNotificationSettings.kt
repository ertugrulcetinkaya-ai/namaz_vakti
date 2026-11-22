package com.example.namazvakti.domain.model

enum class PrayerNotificationPrayer(val storageKey: String) {
    IMSAK("imsak"),
    SUNRISE("sunrise"),
    DHUHR("dhuhr"),
    ASR("asr"),
    MAGHRIB("maghrib"),
    ISHA("isha")
}

enum class PrayerNotificationType {
    REMINDER,
    AT_TIME
}

data class PrayerNotificationSettings(
    val notificationsEnabled: Boolean = false,
    val enabledPrayers: Set<PrayerNotificationPrayer> = DEFAULT_ENABLED_PRAYERS,
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val religiousDaysEnabled: Boolean = false
) {
    init {
        require(leadMinutes in ALLOWED_LEAD_MINUTES) {
            "Unsupported notification lead time: $leadMinutes"
        }
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 0
        val ALLOWED_LEAD_MINUTES = setOf(0, 5, 10, 15, 30, 45)
        val DEFAULT_ENABLED_PRAYERS = setOf(
            PrayerNotificationPrayer.IMSAK,
            PrayerNotificationPrayer.DHUHR,
            PrayerNotificationPrayer.ASR,
            PrayerNotificationPrayer.MAGHRIB,
            PrayerNotificationPrayer.ISHA
        )
    }
}
