# Gizlilik

Namaz Vakti hesap oluşturmaz ve analitik kullanmaz.

## Ağ istekleri

- Diyanet verisine erişen üçüncü taraf ara servise yalnızca seçili ilçe kimliği gönderilir.
- Yedek hesaplama gerektiğinde AlAdhan API'ye şehir, ülke ve il merkezi koordinatları gönderilir.

## Cihazda saklanan veriler

- Seçili konum DataStore'da saklanır.
- İndirilen namaz vakitleri ve hicri takvim alanları Room önbelleğinde saklanır.
- Bildirim tercihleri DataStore'da saklanır.

Uygulama verileri Android yedeklemesi ve cihaz aktarımı kapsamı dışında bırakılmıştır; konum, vakit önbelleği ve bildirim tercihleri bu yollarla taşınmaz. Bu dışlama [PR #65](https://github.com/ertugrulcetinkaya-ai/namaz_vakti/pull/65) ile eklendi.

Paket kimliği değiştiğinde eski beta kurulumu yeni uygulamadan ayrıdır. Eski uygulamadaki veriler yeni kuruluma otomatik aktarılmaz.
