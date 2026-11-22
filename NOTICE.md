# Üçüncü taraf atıfları

Depodaki uygulama kodu [MIT lisansı](LICENSE) altındadır. Aşağıdaki servisler, API yanıtları ve veri kümeleri bu MIT lisansının kapsamına dahil değildir; her biri kendi hak ve kullanım koşullarına tabidir.

- **Diyanet İşleri Başkanlığı namaz vakti verisi:** Uygulamada [namazvakti.mtopal.dev](https://namazvakti.mtopal.dev/) adlı üçüncü taraf ara servis ve [API belgeleri](https://namazvakti.mtopal.dev/docs.html) üzerinden kullanılır. Ara servis Diyanet İşleri Başkanlığı tarafından işletilmez.
- **AlAdhan API:** Eksik tarihler ve erişim hatalarında yedek hesaplama için [AlAdhan Prayer Times API](https://aladhan.com/prayer-times-api) kullanılır.
- **İl merkezi koordinatları:** brkunver, **Turkish Provinces Dataset v1.0.0**, `data/provinces.csv`, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), [kaynak deposu](https://github.com/brkunver/turkiye-provinces-dataset). Uygulamada yaklaşık idari merkez koordinatları dört ondalık basamağa yuvarlanmıştır.
- **İlçe sınırları ve koordinatları:** OCHA / Harita Genel Komutanlığı, **Türkiye - Subnational Administrative Boundaries (COD-AB)**, [CC BY-IGO](https://data.humdata.org/dataset/cod-ab-tur). İlçe koordinatları ilçe poligonlarının eşit alanlı izdüşümde hesaplanan merkezinden türetilmiştir; merkez poligonun dışındaysa poligon içindeki temsil noktası kullanılmıştır.
