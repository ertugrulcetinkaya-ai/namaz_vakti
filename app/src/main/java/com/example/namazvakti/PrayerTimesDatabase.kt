package com.example.namazvakti

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Entity(
    tableName = "prayer_days",
    primaryKeys = ["date", "city", "country", "method", "school"],
    indices = [Index(value = ["city", "country", "method", "school", "date"])]
)
data class PrayerDayEntity(
    val date: String,
    val city: String,
    val country: String,
    val method: Int,
    val school: Int,
    val displayCity: String,
    val locationTimezone: String,
    val prayerTimezone: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val hijriText: String?,
    val fetchedAtEpochMillis: Long,
    val source: String
)

@Dao
interface PrayerDayDao {
    @Query(
        """SELECT * FROM prayer_days
            WHERE date = :date AND city = :city AND country = :country
              AND method = :method AND school = :school
            LIMIT 1"""
    )
    suspend fun find(
        date: String,
        city: String,
        country: String,
        method: Int,
        school: Int
    ): PrayerDayEntity?

    @Query(
        """SELECT * FROM prayer_days
            WHERE date <= :date AND city = :city AND country = :country
              AND method = :method AND school = :school
            ORDER BY date DESC
            LIMIT 1"""
    )
    suspend fun latestOnOrBefore(
        date: String,
        city: String,
        country: String,
        method: Int,
        school: Int
    ): PrayerDayEntity?

    @Query(
        """SELECT COUNT(*) FROM prayer_days
            WHERE date BETWEEN :startDate AND :endDate
              AND city = :city AND country = :country
              AND method = :method AND school = :school"""
    )
    suspend fun countBetween(
        startDate: String,
        endDate: String,
        city: String,
        country: String,
        method: Int,
        school: Int
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<PrayerDayEntity>)

    @Query("DELETE FROM prayer_days")
    suspend fun clearAll()
}

@Database(entities = [PrayerDayEntity::class], version = 1, exportSchema = true)
abstract class PrayerTimesDatabase : RoomDatabase() {
    abstract fun prayerDayDao(): PrayerDayDao

    companion object {
        fun create(context: Context): PrayerTimesDatabase = Room.databaseBuilder(
            context.applicationContext,
            PrayerTimesDatabase::class.java,
            "prayer_times.db"
        ).build()
    }
}

internal fun CachedPrayerDay.toEntity() = PrayerDayEntity(
    date = date.toString(),
    city = location.city,
    country = location.country,
    method = settings.method,
    school = settings.school,
    displayCity = location.displayCity,
    locationTimezone = location.timezone.id,
    prayerTimezone = timezone.id,
    fajr = prayerTimes.fajr.toString(),
    sunrise = prayerTimes.sunrise.toString(),
    dhuhr = prayerTimes.dhuhr.toString(),
    asr = prayerTimes.asr.toString(),
    maghrib = prayerTimes.maghrib.toString(),
    isha = prayerTimes.isha.toString(),
    hijriText = hijriText,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    source = source.name
)

internal fun PrayerDayEntity.toDomain() = CachedPrayerDay(
    date = LocalDate.parse(date),
    location = PrayerLocation(
        city = city,
        country = country,
        displayCity = displayCity,
        timezone = ZoneId.of(locationTimezone)
    ),
    timezone = ZoneId.of(prayerTimezone),
    settings = PrayerCalculationSettings(method, school),
    prayerTimes = PrayerTimes(
        fajr = LocalTime.parse(fajr),
        sunrise = LocalTime.parse(sunrise),
        dhuhr = LocalTime.parse(dhuhr),
        asr = LocalTime.parse(asr),
        maghrib = LocalTime.parse(maghrib),
        isha = LocalTime.parse(isha)
    ),
    hijriText = hijriText,
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
    source = runCatching { PrayerDataSource.valueOf(source) }
        .getOrDefault(PrayerDataSource.LEGACY_CACHE)
)
