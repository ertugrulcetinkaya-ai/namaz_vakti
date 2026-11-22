# Namaz Vakti

Namaz Vakti, Türkiye'deki şehirler ve ilçeler için namaz vakitlerini gösteren açık kaynak bir Android uygulaması ve ana ekran widget'ıdır.

## Özellikler

- Türkiye'deki 81 il ve 869 ilçe arasından konum seçimi; Türkçe karakterlerden bağımsız arama
- Jetpack Compose ve Material 3 ile günlük vakitler, Türkçe ve hicri tarih, canlı geri sayım
- Geçmiş, aktif ve gelecek vakitleri ayırt eden ana ekran; Android 12+ dinamik renkler ve sistem açık/koyu teması
- Ana ekrandan şehir/ilçe arama ve bildirim ayarlarına erişim
- İlçe vakitleri için Diyanet verisi (üçüncü taraf ara servis üzerinden); eksik veya erişilemeyen tarihler için AlAdhan yedeği
- AlAdhan ile il merkezi koordinatları kullanılarak yapılan yedek hesaplamanın arayüzde il merkezi olarak belirtilmesi
- Dini günler ve kandiller için ana ekran duyuruları ve isteğe bağlı hatırlatma bildirimleri
- Günlük ve aylık vakit tablosu, Ramazan imsakiyesi, iftar sütunu ve bayram tarihleri
- İkindi vakti için varsayılan Şafii/standart hesaplama (`school=0`)
- Room'da timezone duyarlı ve çevrimdışı vakit önbelleği
- Önbellek saklama aralığı: bugünden 90 gün öncesi ile 13 ay sonrası; aralık dışındaki kayıtlar temizlenir
- Ayın son yedi gününde sonraki ayı otomatik önceden indirme; aylık tablo aylarını isteğe bağlı indirme
- Veri kaynağı, hesaplama yöntemi, il merkezi yedek notu ve son güncelleme bilgisini gösterme
- 2×1, 4×1 ve 4×2+ boyutlarında aktif vakti işaretleyen widget; sistem saati ve vakit sınırlarında otomatik güncelleme
- Sıradaki vakti `Chronometer` ile canlı gösteren widget; uygulama saniyede bir çalışmaz
- Android 12+ boyuta duyarlı `RemoteViews`, dinamik sistem renkleri ve sistem köşe yarıçapları
- Vakit başına açılabilen bildirimler, önceden hatırlatma ve ses/titreşim ayarları
- CI doğrulaması ve imzalı beta artifact üretimi

## Veri kaynakları ve önbellek

İlçe vakitleri [namazvakti.mtopal.dev](https://namazvakti.mtopal.dev/docs.html) üzerinden sunulan Diyanet verisini kullanır. Bu adres, Diyanet İşleri Başkanlığı'nın resmi servisi değil, veriye erişim sağlayan üçüncü taraf bir ara servistir. İsteklerde yalnızca seçili ilçe kimliği gönderilir. Ara servis bugünden yıl sonuna kadar veri döndürür; geçmiş/gelecek tarihler ve erişim hatalarında yedek hesaplama için [AlAdhan API](https://aladhan.com/prayer-times-api) kullanılır. AlAdhan isteğine şehir, ülke ve il merkezi koordinatları eklenir.

İlçe listesi, `tools/diyanet_locations` tarafından `tools/diyanet_snapshot` içindeki ara servis yanıtlarından üretilir. Diyanet kayıtları AlAdhan sonuçları tarafından üzerine yazılmaz. Hicri Ramazan aralıklarında Diyanet takvimindeki kayıtlar, AlAdhan `hToGCalendar` tahminlerine öncelik verir.

`PrayerTimesStore` seçili konumu DataStore'da, günlük vakitleri ve sayısal hicri alanları Room'da `konum + tarih + yöntem + mezhep` anahtarıyla saklar. Room şemasının 1'den 2'ye geçişi açık migration kullanır. Namaz veri sürümü `4`'tür. Veri sürümü değiştiğinde Room vakit önbelleği ilk kullanımda temizlenir; cihaz güncelleme sırasında çevrimdışıysa yeni vakit verisinin yeniden indirilmesi gerekir.

## Gizlilik ve lisans

Uygulama hesap veya analitik kullanmaz. Ağ istekleri, cihazda saklanan veriler ve yedekleme davranışı için [PRIVACY.md](PRIVACY.md); üçüncü taraf veri ve servis atıfları için [NOTICE.md](NOTICE.md) dosyasına bakın. Depodaki kod [MIT lisanslıdır](LICENSE); üçüncü taraf servisler ve veriler MIT lisansının kapsamında değildir.

## Mimari

- `app`: application ve composition root (`AppContainer`)
- `domain/model`, `domain/policy`, `domain/port`: platformdan bağımsız modeller, karar politikaları ve uygulama sözleşmeleri
- `data/local`, `data/remote`, `data/repository`: DataStore/Room, Diyanet ve AlAdhan API istemcileri ve repository
- `ui/main`: Compose Activity/ekranları, ViewModel ve typed UI state
- `widget/alarm`, `widget/renderer`, `widget/worker`: alarm/scheduler, `RemoteViews` render'ı ve WorkManager

Widget 2×1 boyutta sıradaki vakit ve geri sayımı, 4×1 boyutta şehir/hicri tarih ile altı vakitlik ızgarayı, 4×2 ve daha büyük boyutlarda miladi/hicri tarih, belirgin geri sayım ve vakit ızgarasını gösterir. Ramazan günlerinde büyük boyuta Diyanet takvim aralığından iftar veya sahur bilgisi eklenir. Tüm boyutlar uygulamayı açar.

Geri sayım `RemoteViews` içindeki Android `Chronometer` ile çalışır; ana uygulama her saniye uyanmaz. Vakit sınırlarında alarm widget'ı yeniden çizer. Yarın önbellekte yoksa yatsı sonrasında geri sayım gösterilmez; eski veride de geri sayım kapalı kalır. Android 8–11 boyut seçimini widget seçeneklerinden, renkleri uygulamanın yeşil temasından alır.

## Paket kimliği ve beta yükseltmesi

Uygulamanın kalıcı paket kimliği `io.github.ertugrulcetinkaya.namazvakti`'dir. Mevcut beta kullanıcıları yeni sürümü yeni kimlikle kurmalıdır: eski `com.example.namazvakti` uygulaması ayrı bir uygulama olarak kalır ve yeni sürüme güncellenmez. Yeni sürümü kurup doğruladıktan sonra eski sürümü ayrıca kaldırın. Konum, önbellek ve bildirim tercihleri uygulama kimliği değiştiği için yeni kuruluma otomatik aktarılmaz.

## Gereksinimler ve derleme

- JDK 17
- Android SDK 36
- Android 8.0 veya üzeri cihaz/emülatör (`minSdk 26`)

Derleme zinciri AGP 8.13.2, Gradle 8.14.7, Kotlin 2.4.21 ve KSP 2.3.13 kullanır. Gradle dağıtımı checksum ile doğrulanır; `gradle/verification-metadata.xml` çözümlenen bağımlılıkların SHA-256 doğrulamasını içerir. Gradle build cache ve configuration cache yerel ve CI derlemelerinde etkindir.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Kurulu uygulamayı bağlı cihazda açmak için:

```bash
adb shell am start -n io.github.ertugrulcetinkaya.namazvakti/com.example.namazvakti.ui.main.MainActivity
```

## Test ve doğrulama

```bash
./gradlew testDebugUnitTest lint assembleRelease
```

Bağlı cihazda küçültülmüş release benzeri varyanttaki instrumentation testlerini çalıştırmak için:

```bash
./gradlew connectedMinifiedAndroidTest
```

Gradle Managed Device emulator'ında CI ile aynı instrumentation testleri için:

```bash
./gradlew --dependency-verification=strict pixel2api35MinifiedAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Managed-device ve bağlı cihaz testleri debug keystore ile imzalanan, R8 ve resource shrinking etkin `minified` varyantta çalışır. Testler küçük, orta ve büyük widget'ları güncel, eski ve önbelleksiz durumlarda inflate edip ölçer; render görüntülerini uygulamanın dış dosya klasörüne kaydeder. Legacy Gson cache migration'ı ve widget reflection yolları da küçültülmüş çalışma zamanında sınanır. Compose UI smoke testi eklenmedi: minified testte Compose test rule, uygulama APK'sında R8 tarafından yeniden adlandırılan veya kaldırılan `InfiniteAnimationPolicy`, `IntObjectMapKt.mutableIntObjectMapOf` ve `ViewTreeLifecycleOwner` API'lerine erişemedi.

Unit testler cache şema dönüşümünü, Room migration'ını, retention penceresini, Ramazan tarihi önceliğini, API hicri alan doğrulamasını, timezone'a göre tarih üretimini, geri sayım sınırlarını, coroutine iptalini ve eşzamanlı kullanıcı işlemlerini kapsar. Legacy DataStore JSON alanları `@SerializedName` ile sabitlenmiştir; migration davranışı JVM ve minified instrumentation regression testleriyle denetlenir.

## Beta sürüm üretimi

Beta sürüm değerleri varsayılan olarak şöyledir:

- `versionName`: `0.1.0-beta.1`
- `versionCode`: `100`

GitHub Beta Release workflow'u manuel version code kabul etmez; mevcut `100` baseline'ının üzerine workflow run numarasını ekleyerek monoton bir version code üretir. Daha yüksek bir version code yayınlanırsa `VERSION_CODE_BASE` değeri bir sonraki release'ten önce yükseltilmelidir.

Yerelde farklı değerlerle build almak için:

```bash
VERSION_NAME=0.1.0-beta.2 VERSION_CODE=101 ./gradlew assembleRelease bundleRelease
```

GitHub'daki `Beta Release` workflow'u manuel çalıştırılabilir veya `v*` etiketi push edildiğinde tetiklenir. Doğrulama job'ı debug APK ve Android test APK üretir; release job'ı yalnızca imzalama secret'ları mevcutsa release APK ve AAB yükler. Secret'lar tanımlı değilse release job'ı başarısız olur ve unsigned release artifact üretmez.

İmzalı beta için repository `release` environment'ında şu secret'lar tanımlanmalıdır:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64`, keystore dosyasının base64 kodlanmış içeriğidir. Keystore veya parola değerleri repository'ye commit edilmemelidir.
