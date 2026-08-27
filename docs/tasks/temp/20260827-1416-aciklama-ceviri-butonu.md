---
schema: task-plan/v1
id: 20260827-1416-aciklama-ceviri-butonu
status: completed
created_at: 2026-08-27T11:16:04Z
updated_at: 2026-08-27T11:34:23Z
base_commit: 03f77d54fc283a1ca53070e917a28001324d716e
---

# Açıklama Metinlerine Çeviri Butonu

## Goal
Film/dizi detay ekranındaki ve kategori öğelerinin açtığı meta kartındaki açıklamaya bir çeviri butonu eklemek. Açıklama, API anahtarı gerektirmeyen Google ML Kit cihaz içi çeviri akışıyla CloudStream’in seçili uygulama diline çevrilecek; telefon ve TV davranışları korunacak.

## Repository Context
- Detay açıklaması `ResultFragmentPhone.onBindingCreated` ve `ResultFragmentTv.onBindingCreated` içinde `ResultData.plotText` üzerinden `result_description` alanına bağlanıyor; ilgili düzenler `fragment_result.xml` ve `fragment_result_tv.xml`.
- Kategori/arama/kütüphane öğelerinin preview meta kartı `MainActivity.loadPopup` ve `MainActivity.onCreate` gözlemcileriyle yönetiliyor; açıklama `bottom_resultview_preview.xml` ve TV karşılığındaki `resultview_preview_description` alanında gösteriliyor.
- Hedef dil `SettingsGeneral.getCurrentLocale(context)` ile uygulama konfigürasyonundan alınabilir. Yeni entegrasyon yalnızca Android `app` katmanında kalmalı; `library` genel API/ABI yüzeyine taşınmamalı.
- Google ML Kit Translation API anahtarı istemez; kaynak dili Language Identification ile belirler, gerekli dil modellerini isteğe bağlı indirir ve model hazır olduğunda cihaz üzerinde çalışır.

## Tasks

- [x] **T01 — ML Kit bağımlılıklarını sürüm kataloğuna ekle**
  - Güncel kararlı `com.google.mlkit:translate` ve dil tanıma bağımlılıklarını `gradle/libs.versions.toml` üzerinden tanımlayıp `app` modülüne bağla.
  - Files/symbols: `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - Invariants: API anahtarı, gizli değer veya uzak çeviri servisi eklenmeyecek; bağımlılık `library` KMP/ABI yüzeyine sızmayacak.

- [x] **T02 — Yaşam döngüsü güvenli açıklama çeviri bileşenini oluştur**
  - Kaynak dili algılayan, uygulama dil etiketini ML Kit hedef diline dönüştüren, gerekli modeli indiren ve açıklamayı çeviren app-katmanı bileşeni ekle.
  - Boş metin, algılanamayan/ML Kit tarafından desteklenmeyen dil, kaynak-hedef eşitliği, ağ/model indirme ve çeviri hatalarını kontrollü sonuçlara dönüştür; ML Kit istemcilerini kapat ve iptal edilen/eskimiş isteğin UI’ı güncellemesini engelle.
  - Files/symbols: `app/src/main/java/com/lagradost/cloudstream3/ui/result/` altında yeni, sonuç ekranınca paylaşılabilir bir çeviri sınıfı

- [x] **T03 — Çeviri durumunu ve kullanıcı geri bildirimini tanımla**
  - Orijinal metin, yükleniyor, çevrildi ve hata durumlarını UI katmanlarının ortak kullanabileceği küçük bir durum/yardımcı akışla temsil et.
  - Çevir, çevriliyor ve çeviri başarısız/uygun değil metinlerini yalnızca ana `values/strings.xml` içine ekle; Weblate tarafından yönetilen çeviri dosyalarını elle değiştirme.
  - Files/symbols: `app/src/main/res/values/strings.xml`, yeni çeviri bileşeni

- [x] **T04 — Telefon detay ekranine çeviri butonu ekle**
  - `result_description` yakınına mevcut Material stiline uyumlu, açıklama mevcut olduğunda görünen bir çeviri butonu yerleştir; tıklamada hedef dili `getCurrentLocale` ile alıp çeviriyi başlat.
  - Yükleme sırasında tekrar tıklamayı engelle, başarılı sonucu açıklamada göster ve yeni içerik bağlandığında orijinal metin/durumla sıfırla; mevcut açıklamayı genişletme davranışını koru.
  - Files/symbols: `fragment_result.xml`, `ResultFragmentPhone.onBindingCreated`
  - Depends on: T02, T03

- [x] **T05 — TV detay ekranine odak uyumlu çeviri butonu ekle**
  - `fragment_result_tv.xml` içindeki açıklama alan(lar)ına TV’de fokus alınabilen çeviri aksiyonu ekle ve `nextFocus` bağlantılarını mevcut oynat/açıklama/cast akışını bozmayacak şekilde güncelle.
  - `ResultFragmentTv.onBindingCreated` içinde telefonla aynı çeviri durumunu kullan; dialog ile tam açıklama gösterimi ve emulator/TV ayrımını koru.
  - Files/symbols: `fragment_result_tv.xml`, `ResultFragmentTv.onBindingCreated`
  - Depends on: T02, T03

- [x] **T06 — Telefon ve TV meta preview kartlarına çeviri aksiyonu ekle**
  - Her iki `bottom_resultview_preview` düzeninde açıklama yakınına uygun buton ekle; `MainActivity` preview gözlemcisinde geçerli `ResultData.plotText` için çeviri akışını bağla.
  - Popup kapanınca veya başka içerik açılınca devam eden işi iptal et, çevrilmiş metni taşımadan orijinal açıklama ve buton durumunu sıfırla; mevcut açıklama dialogunu çevrilmiş güncel metinle aç.
  - Files/symbols: `bottom_resultview_preview.xml`, `bottom_resultview_preview_tv.xml`, `MainActivity.onCreate`, `MainActivity.hidePreviewPopupDialog`
  - Depends on: T02, T03

- [x] **T07 — Hata ve uyumluluk kenarlarını tamamla**
  - İnternet/model yokluğu, desteklenmeyen uygulama dili, kaynak ve hedef dilin aynı olması ile hızlı ardışık içerik değişimlerinde crash veya yanlış karta metin yazılmamasını garanti et.
  - Telefon/TV görünürlük, erişilebilir etiket ve fokus davranışlarını mevcut ViewBinding/XML yaklaşımına uygun tut; kullanıcıya teknik exception ayrıntısı göstermeden proje loglama kalıbını kullan.
  - Depends on: T04, T05, T06

## Validation

- [x] **V01 — `./gradlew :app:assemblePrereleaseDebug` ile Kotlin, kaynak, ViewBinding ve bağımlılık çözümleme hatalarını kontrol et.**
- [x] **V02 — Değişen Kotlin dosyalarında IDE/LSP diagnostics çalıştır ve kalan error seviyesindeki tanıları gider.**
- [x] **V03 — Kullanıcı talebi gereği unit test ve emulator/instrumented test çalıştırma; final raporda bu kontrollerin bilinçli olarak uygulanmadığını belirt.**

## Implementation Notes
- ML Kit modelleri ilk kullanımda indirileceği için ilk çeviri daha uzun sürebilir; UI yükleniyor durumunu göstermeli ve indirme/çeviri başarısızlığında orijinal açıklamayı korumalıdır.
- Çeviri kalıcı olarak `LoadResponse`, bookmark veya senkronizasyon verisine yazılmamalı; yalnızca açık UI yüzeyinin geçici gösterim durumu olmalıdır.
- Note: ML Kit Translator ve LanguageIdentifier her istek sonunda kapatılır; görünüm veya içerik değiştiğinde ilgili coroutine iptal edilerek eski isteğin UI güncellemesi önlenir.
- Note: `:app:assemblePrereleaseDebug` JDK 17 ve yerel Android SDK ile başarıyla tamamlandı. LSP taramasında yeni hata bulunmadı; sadece mevcut dosyalardaki inspection uyarıları görüldü.
- Note: Plan gereği unit ve emulator/instrumented testler çalıştırılmadı.
