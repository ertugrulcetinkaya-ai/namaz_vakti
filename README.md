# Namaz Vakti

Namaz Vakti, Türkiye'deki şehirler için günlük namaz vakitlerini gösteren ve ana ekranda güncel vakti vurgulayan açık kaynak bir Android uygulaması ve widget'ıdır.

## Özellikler

- Şehir seçimi ve Türkçe karakterlerden bağımsız arama
- İkindi vakti için varsayılan Şafii/standart hesaplama (`school=0`)
- Yapılandırılmış, timezone duyarlı namaz vakti cache'i
- Güncellik ve hata durumlarını gösteren uygulama arayüzü
- Aktif vakti vurgulayan, stale veriyi belirten ana ekran widget'ı
- Vakit sınırlarında ve sistem saati değişikliklerinde otomatik widget güncellemesi
- CI doğrulaması ve isteğe bağlı imzalı beta artifact üretimi

## Mimari

- `PrayerTimesApi` uzak API isteğini ve yanıt ayrıştırmasını yönetir.
- `PrayerTimesRepository` API sonucu ile yerel cache'i koordine eder.
- `PrayerTimesStore` DataStore üzerinden konum ve yapılandırılmış cache saklar.
- `PrayerViewModel` uygulama ekranının typed UI state'ini üretir.
- `PrayerWidgetRenderer` yalnızca `RemoteViews` oluşturur.
- `PrayerWidgetScheduler` benzersiz WorkManager işlerini ve sonraki vakit sınırını planlar.
- `NamazVaktiApp.AppContainer` uygulama bağımlılıklarını tek noktadan sağlar.

## Gereksinimler

- JDK 17
- Android SDK 34
- Android 8.0 veya üzeri cihaz/emülatör (`minSdk 26`)

## Derleme ve kurulum

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Uygulamayı bağlı cihazda açmak için:

```bash
adb shell am start -n com.example.namazvakti/.MainActivity
```

## Test ve doğrulama

Unit test, lint, debug APK ve Android test APK'sını birlikte doğrulamak için:

```bash
./gradlew lint test assembleDebug assembleAndroidTest
```

Widget `RemoteViews` testlerini bağlı cihazda çalıştırmak için:

```bash
./gradlew connectedDebugAndroidTest
```

Instrumentation testleri fresh, stale ve cache bulunmayan widget durumlarını kontrol eder. Render smoke testi ise gerçek `RemoteViews` ağacını inflate eder, ölçer ve bitmap üzerine çizerek görünümün cihazda oluşturulabildiğini doğrular.

## Beta sürüm üretimi

Beta sürüm değerleri varsayılan olarak şunlardır:

- `versionName`: `0.1.0-beta.1`
- `versionCode`: `100`

Yerelde farklı değerlerle build almak için:

```bash
VERSION_NAME=0.1.0-beta.2 VERSION_CODE=101 ./gradlew assembleRelease bundleRelease
```

GitHub'daki `Beta Release` workflow'u manuel çalıştırılabilir veya `v*` etiketi push edildiğinde tetiklenir. Debug APK, Android test APK, release APK ve AAB dosyaları Actions artifact'i olarak yüklenir.

Signing secret'ları tanımlı değilse release artifact'leri unsigned üretilir ve workflow başarısız olmaz. İmzalı beta için repository secret'larına şu değerler eklenebilir:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64`, keystore dosyasının base64 kodlanmış içeriği olmalıdır. Keystore veya parola değerleri repository'ye commit edilmemelidir.
