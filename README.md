# Namaz Vakti

Namaz Vakti, Türkiye'deki şehirler için günlük namaz vakitlerini gösteren ve ana ekranda güncel vakti vurgulayan açık kaynak bir Android uygulaması ve widget'ıdır.

## Özellikler

- Şehir seçimi ve Türkçe karakterlerden bağımsız arama
- İkindi vakti için varsayılan Şafii/standart hesaplama (`school=0`)
- Room üzerinde timezone duyarlı ve çevrimdışı namaz vakti takvimi
- Cache retention: bugünden 90 gün öncesi ile 60 gün sonrası dışındaki kayıtlar temizlenir
- Ayın son yedi gününde sonraki ayı otomatik önceden indirme
- Veri kaynağı, hesaplama yöntemi ve son güncelleme bilgisini gösterme
- Güncellik ve hata durumlarını gösteren uygulama arayüzü
- Aktif vakti vurgulayan, stale veriyi belirten ana ekran widget'ı
- Vakit sınırlarında ve sistem saati değişikliklerinde otomatik widget güncellemesi
- CI doğrulaması ve imzalı beta artifact üretimi

## Mimari

Kod sınırları paketlerle görünür hale getirilmiştir:

- `app`: application ve composition root (`AppContainer`)
- `domain/model`, `domain/policy`, `domain/port`: platformdan bağımsız modeller, karar politikaları ve uygulama sözleşmeleri
- `data/local`, `data/remote`, `data/repository`: DataStore/Room, yalnız aylık takvim API'si ve repository
- `ui/main`: Activity, ViewModel ve typed UI state
- `widget/alarm`, `widget/renderer`, `widget/worker`: alarm/scheduler, `RemoteViews` render'ı ve WorkManager

`PrayerTimesRepository` aylık API sonucunu doğrular, bugünün kaydını seçer ve sonraki ay ön-getirmesini koordine eder. `PrayerTimesStore` konumu DataStore'da, günlük vakitleri Room'da `konum + tarih + yöntem + mezhep` anahtarıyla saklar. Eski tek günlük DataStore cache'i ilk kullanımda otomatik olarak Room'a taşınır. `PrayerCachePolicy` timezone duyarlı cache güncelliğinin tek karar noktasıdır.

## Gereksinimler

- JDK 17
- Android SDK 36
- Android 8.0 veya üzeri cihaz/emülatör (`minSdk 26`)

Derleme zinciri AGP 8.13.2, Gradle 8.14.5, Kotlin 2.4.20 ve KSP 2.3.10 kullanır. Gradle dağıtımı checksum ile doğrulanır; `gradle/verification-metadata.xml` çözümlenen bağımlılıkların SHA-256 doğrulamasını içerir. Gradle build cache ve configuration cache yerel ve CI derlemelerinde etkindir.

Uygulama verileri backup ve device-transfer kapsamı dışında bırakılmıştır; konum ve cache cihazlar arasında taşınmaz.

## Derleme ve kurulum

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Uygulamayı bağlı cihazda açmak için:

```bash
adb shell am start -n com.example.namazvakti/.ui.main.MainActivity
```

## Test ve doğrulama

Unit test, lint, debug APK ve Android test APK'sını birlikte doğrulamak için:

```bash
./gradlew --dependency-verification=strict lint test assembleDebug assembleAndroidTest
```

Widget `RemoteViews` testlerini bağlı cihazda çalıştırmak için:

```bash
./gradlew connectedDebugAndroidTest
```

Gradle Managed Device emulator'ında CI ile aynı instrumented testleri çalıştırmak için:

```bash
./gradlew --dependency-verification=strict pixel2api35DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Instrumentation testleri fresh, stale ve cache bulunmayan widget durumlarını kontrol eder. Render smoke testi ise gerçek `RemoteViews` ağacını inflate eder, ölçer ve bitmap üzerine çizerek görünümün cihazda oluşturulabildiğini doğrular.

Unit testler ayrıca cache şema dönüşümünü, retention penceresini, API timezone'una göre tarih üretimini, coroutine iptalini ve eşzamanlı kullanıcı işlemlerinde son seçimin kazanmasını doğrular.

Release küçültme değerlendirmesinde unsigned release APK, R8 ve resource shrinking kapalıyken
4,734,982 byte; etkinleştirildikten sonra 1,267,701 byte ölçülmüştür (yaklaşık %73 azalma).
Bu nedenle release build'inde `isMinifyEnabled` ve `isShrinkResources` açıktır; Gson DTO'ları,
Room entity'si ve WorkManager worker'ı için gerekli keep kuralları `app/proguard-rules.pro`
dosyasındadır.

## Beta sürüm üretimi

Beta sürüm değerleri varsayılan olarak şunlardır:

- `versionName`: `0.1.0-beta.1`
- `versionCode`: `100`

Yerelde farklı değerlerle build almak için:

```bash
VERSION_NAME=0.1.0-beta.2 VERSION_CODE=101 ./gradlew assembleRelease bundleRelease
```

GitHub'daki `Beta Release` workflow'u manuel çalıştırılabilir veya `v*` etiketi push edildiğinde tetiklenir. Doğrulama job'ı debug APK ve Android test APK üretir; release job'ı yalnızca imzalama secret'ları mevcutsa release APK ve AAB yükler.

Release job'ı signing secret'ları tanımlı değilse başarısız olur ve unsigned release artifact üretmez. İmzalı beta için repository `release` environment'ına şu secret'lar eklenmelidir:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64`, keystore dosyasının base64 kodlanmış içeriği olmalıdır. Keystore veya parola değerleri repository'ye commit edilmemelidir.
