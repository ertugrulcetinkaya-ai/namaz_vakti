# Namaz Vakti

Namaz Vakti, Türkiye'deki şehirler için günlük namaz vakitlerini gösteren ve ana ekranda güncel vakti vurgulayan açık kaynak bir Android uygulaması ve widget'ıdır.

## Özellikler

- Şehir seçimi ve Türkçe karakterlerden bağımsız arama
- Jetpack Compose ve Material 3 ile bugünün vakitleri, Türkçe tarih, hicri tarih ve canlı geri sayım
- Geçmiş, aktif ve gelecek vakitleri ayırt eden kaydırılabilir ana ekran; Android 12+ dinamik renkler ve sistem açık/koyu teması
- Ana ekrandan şehir arama ve bildirim ayarlarına erişim
- İkindi vakti için varsayılan Şafii/standart hesaplama (`school=0`)
- Room üzerinde timezone duyarlı ve çevrimdışı namaz vakti takvimi
- AlAdhan method 13 vakitleri ve hicri takvim isteklerinde `calendarMethod=DIYANET`
- Resmi olarak yayınlanmış Ramazan başlangıcı/bayram tarihlerine öncelik, yayınlanmayan yıllar için tahmini aralık
- Günlük ve aylık vakit tablosu ile Ramazan imsakiyesi, iftar sütunu, Kadir Gecesi ve bayram tarihi
- Cache retention: bugünden 90 gün öncesi ile 13 ay sonrası dışındaki kayıtlar temizlenir
- Ayın son yedi gününde sonraki ayı otomatik önceden indirme; aylık tablo aylarını isteğe bağlı indirme
- Veri kaynağı, hesaplama yöntemi ve son güncelleme bilgisini gösterme
- Kaynak, yöntem, güncellik ve hata durumlarını gösteren Compose arayüzü
- 2×1, 4×1 ve 4×2+ boyutlarında, aktif vakti işaretleyen ana ekran widget'ı
- Sıradaki vakti `Chronometer` ile canlı gösteren widget; uygulama saniyede bir çalışmaz
- Android 12+ responsive `RemoteViews`, dinamik sistem renkleri ve sistem köşe yarıçapları
- Vakit sınırlarında ve sistem saati değişikliklerinde otomatik widget güncellemesi
- İsteğe bağlı, vakit başına açılabilen namaz vakti bildirimleri ve önceden hatırlatma
- CI doğrulaması ve imzalı beta artifact üretimi

## Mimari

Kod sınırları paketlerle görünür hale getirilmiştir:

- `app`: application ve composition root (`AppContainer`)
- `domain/model`, `domain/policy`, `domain/port`: platformdan bağımsız modeller, karar politikaları ve uygulama sözleşmeleri
- `data/local`, `data/remote`, `data/repository`: DataStore/Room, yalnız aylık takvim API'si ve repository
- `ui/main`: Compose Activity/ekranları, ViewModel ve typed UI state
- `widget/alarm`, `widget/renderer`, `widget/worker`: alarm/scheduler, `RemoteViews` render'ı ve WorkManager

`PrayerTimesRepository` aylık API sonucunu doğrular, isteğe bağlı ay indirmelerini ve sonraki ay ön-getirmesini koordine eder. Hicri Ramazan aralıklarında resmi Diyanet kayıtları AlAdhan `hToGCalendar` tahminlerinin önüne geçer. `PrayerTimesStore` konumu DataStore'da, günlük vakitleri ve sayısal hicri alanları Room'da `konum + tarih + yöntem + mezhep` anahtarıyla saklar. Room sürüm 1'den 2'ye açık migration kullanır; namaz veri sürümü 3 olduğundan eski hesaplama ve hicri metin cache'leri ilk kullanımda bir kez silinir. `PrayerCachePolicy` timezone duyarlı cache güncelliğinin tek karar noktasıdır.

## Ana ekran widget'ı

Widget 2×1 boyutta sıradaki vakit ve geri sayımı, 4×1 boyutta şehir/hicri tarih ile altı vakitlik ızgarayı, 4×2 ve daha büyük boyutlarda miladi/hicri tarih, belirgin geri sayım ve vakit ızgarasını gösterir. Ramazan günlerinde büyük boyuta Diyanet takvim aralığından iftar veya sahur bilgisi eklenir. Tüm boyutlar uygulamayı açar.

Geri sayım `RemoteViews` içindeki Android `Chronometer` ile çalışır; ana uygulama her saniye uyanmaz. Vakit sınırlarında mevcut alarm widget'ı yeniden çizer. Yarın önbellekte yoksa yatsı sonrasında geri sayım gösterilmez; eski veride de geri sayım kapalı kalır. Android 12+ boyuta göre responsive `RemoteViews` ve sistem dinamik renklerini kullanır. Android 8–11 boyut seçimini widget seçeneklerinden, renkleri uygulamanın yeşil temasından alır.

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

Unit test, lint, debug/minified/release APK'larını ve minified Android test APK'sını birlikte doğrulamak için:

```bash
./gradlew --dependency-verification=strict lint test assembleDebug assembleMinified assembleMinifiedAndroidTest assembleRelease
```

Widget `RemoteViews` testlerini bağlı cihazda çalıştırmak için:

```bash
./gradlew connectedMinifiedAndroidTest
```

Gradle Managed Device emulator'ında CI ile aynı instrumented testleri çalıştırmak için:

```bash
./gradlew --dependency-verification=strict pixel2api35MinifiedAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Managed-device testleri debug keystore ile imzalanan, R8/resource shrinking etkin `minified` varyantında çalışır. Böylece legacy Gson cache migration'ı ve widget reflection yolları küçültülmüş runtime üzerinde de doğrulanır. Instrumentation testleri küçük, orta ve büyük widget'ı fresh, stale ve cache bulunmayan durumlarda inflate eder, gerçek boyutlarında ölçer ve PNG olarak dış uygulama klasörüne kaydeder. Render görüntüleri `review_bundles/widget_refresh_20261105/` altında tutulur ve commit edilmez. Android 12+ açık/koyu dinamik renkleri ile API 30 eski renk teması ayrıca görüntülenir.

Unit testler ayrıca cache şema dönüşümünü, Room migration'ını, 13 aylık retention penceresini, resmi Ramazan tarih önceliğini ve tahmini fallback'ı, API hicri alan doğrulamasını, timezone'a göre tarih üretimini, geri sayım sınırlarını, coroutine iptalini ve eşzamanlı kullanıcı işlemlerinde son seçimin kazanmasını doğrular. Compose UI smoke testi eklenmedi: managed minified testte Compose test rule, uygulama APK'sında R8 tarafından yeniden adlandırılan/kaldırılan `InfiniteAnimationPolicy`, `IntObjectMapKt.mutableIntObjectMapOf` ve `ViewTreeLifecycleOwner` API'lerine erişemedi.

Compose ana ekranı öncesindeki unsigned release APK `1,296,326` byte, sonrasındaki APK
`3,648,289` byte ölçüldü (`+2,351,963` byte, yaklaşık `%181`). Her iki ölçümde de R8 ve
resource shrinking açıktır. Release build'inde `isMinifyEnabled` ve `isShrinkResources`
açıktır; WorkManager worker'ı için gerekli keep kuralı `app/proguard-rules.pro` dosyasındadır.
Minified instrumented test APK'sı için test runner ve reflection destek kuralları ayrıca
`app/proguard-minified-test-rules.pro` dosyasındadır; production release bu test-only
kurallarını kullanmaz.

Compose ekran görüntüleri (açık/koyu tema, şehir seçimi, bildirim ayarları, yatsı sonrası ve
%200 yazı ölçeği) `review_bundles/compose_home_20261104/` altındadır; klasör gitignore'dadır.

Legacy DataStore JSON alanları `@SerializedName` ile sabitlenmiştir; eski, küçültülmemiş
sürümlerden gelen cache kayıtlarının migration sırasında R8 nedeniyle kaybolmaması için
hem JVM hem de minified instrumented regression testleri vardır.

## Beta sürüm üretimi

Beta sürüm değerleri varsayılan olarak şunlardır:

- `versionName`: `0.1.0-beta.1`
- `versionCode`: `100`

GitHub Beta Release workflow'u manuel version code kabul etmez; mevcut `100` baseline'ının
üzerine workflow run numarasını ekleyerek tek ve monoton bir version code üretir. Daha yüksek
bir version code yayınlanırsa workflow'daki `VERSION_CODE_BASE` değeri bir sonraki release'ten
önce yükseltilmelidir.

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
