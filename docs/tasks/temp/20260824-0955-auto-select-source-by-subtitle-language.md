---
schema: task-plan/v1
id: 20260824-0955-auto-select-source-by-subtitle-language
status: partial
created_at: 2026-08-24T09:55:12+03:00
updated_at: 2026-08-24T10:18:03+03:00
base_commit: 28caec33f5877998c643e438588768b34e71f174
---

# Altyazı Diline Göre Uyumlu Kaynağı Otomatik Seçme

## Goal
Bir içerik ilk kez açılırken, kullanıcının otomatik altyazı diline uygun ve kendi altyazısını taşıyan kullanılabilir kaynak varsa video–altyazı senkronu için o kaynağı otomatik başlat. Açık kullanıcı kaynak/altyazı tercihleri, sonraki bölümde seçili kaynakla devam etme, kalite sıralaması, skip ve failover kuralları daha yüksek öncelikle korunmalı.

## Repository Context
- Önceki geliştirme `preferredSource` ve `preferredSubtitle` değerlerini yalnızca açık kullanıcı tercihi olarak oturum boyunca koruyor; `startPlayer()` tercih edilen kaynak bulunana kadar bekliyor ve bulunamazsa terminal yükleme durumunda mevcut sıralı fallback'i kullanıyor.
- `LinkGenerator.generateLinks()` extractor altyazılarını bağlantılar bilinene kadar tamponlayıp güvenilir tek `ExtractorLink.source` değerini `SubtitleData.source` alanına taşıyor; provider/online/downloaded/embedded altyazılar kaynaksız kalıyor.
- İlk otomatik kaynak bugün `sortLinks(currentQualityProfile)` ve `shouldUseLink` üzerinden seçiliyor. `getAutoSelectSubtitle()` ise aktif kaynağa bağlı ayar dili altyazısını global/download fallback'ından önce seçiyor.
- Link ve altyazılar ayrı LiveData akışlarından asenkron geliyor; dil uyumlu kaynak kararı, eşleşen kullanılabilir link ve kaynak bağlı altyazı birlikte görülmeden kesinleştirilemez.

## Tasks

- [x] **T01 — Kaynak seçim önceliğini saf ve test edilebilir politika haline getir**
  - Sıralı kullanılabilir linkler, açık `preferredSource`, ayarlanan altyazı dili ve `SubtitleData.source` verilerinden başlangıç linkini seçen yardımcı ekle/genişlet.
  - Files/symbols: `PlayerSelection.kt`, `selectPreferredLink`, `SubtitleData.matchesLanguageCode`
  - Invariants: Öncelik `açık oturum kaynak tercihi → ilk açılışta kaynak bağlı dil eşleşmesi → mevcut sıralı ilk kullanılabilir link` olmalı; aynı kaynak içindeki kalite sırası ve `shouldUseLink` korunmalı.

- [x] **T02 — Otomatik kaynak kararını açık kullanıcı tercihinden ayır**
  - Note: Otomatik seçim `preferredSource` değerini değiştirmiyor; kaynak tercihi yalnızca mevcut Apply akışında kaydediliyor.
  - Altyazı dili nedeniyle otomatik seçilen kaynağı `preferredSource` olarak kaydetme; yalnızca kaynak diyaloğundaki Apply işlemi oturum tercihini değiştirmeye devam etsin.
  - Files/symbols: `GeneratorPlayer.preferredSource`, `rememberSourcePreference`, `showMirrorsDialogue`
  - Invariants: Otomatik seçim sonraki bölümlerde kullanıcının daha önce açıkça seçtiği kaynağın veya altyazının önüne geçmemeli.

- [x] **T03 — İlk yüklemede asenkron dil–kaynak eşleşmesini bekle**
  - Açık kaynak tercihi yokken ve otomatik altyazı dili etkinse, eşleşen kaynak bağlı altyazı ile kullanılabilir link birlikte gelirse o linki hemen başlat; eşleşme henüz yoksa yükleme sürerken yüksek öncelikli başka linki erken başlatma.
  - Yükleme Success/Failure ile tamamlanırsa veya kullanıcı skip yaparsa eşleşme bulunmadığında mevcut kalite/fallback başlangıcını çalıştır.
  - Files/symbols: `GeneratorPlayer.startPlayer`, `onBindingCreated`, `currentLinks`, `currentSubtitles`, `loadingLinks`
  - Depends on: T01, T02
  - Invariants: `instance` kontrolleri ve `isPlayerActive` atomik koruması sürmeli; dil ayarı boş/kapalıysa mevcut hızlı başlatma davranışı değişmemeli.

- [x] **T04 — Seçilen kaynak ile otomatik altyazı kararını aynı politikada tut**
  - Dil uyumuyla seçilen link açılırken aynı `source` değerine bağlı dil eşleşen altyazının `getAutoSelectSubtitle()` tarafından seçildiğini; altyazı sonradan gelirse yalnızca aynı kaynak aktifken güvenli biçimde uygulanacağını doğrula/düzenle.
  - Files/symbols: `GeneratorPlayer.loadLink`, `getAutoSelectSubtitle`, `autoSelectFromSettings`, `autoSelectSubtitles`, `isSubtitleForLink`
  - Depends on: T01, T03
  - Invariants: Açık altyazı tercihi → seçili kaynağa bağlı dil altyazısı → downloaded/global fallback sırası ve “altyazı yok” tercihi korunmalı.

- [x] **T05 — Bölüm geçişlerinde önceki kaynak-tercihi kuralını koru**
  - İleri, geri ve bölüm listesinden geçişlerde açık `preferredSource` varsa mevcut davranışla önce onu bekle/seç; yeni dil tabanlı otomatik kaynak seçimini yalnızca açık oturum kaynak tercihi bulunmayan ilk seçim yolunda uygula.
  - Tercih edilen kaynak yeni bölümde bulunamazsa önceki geliştirmedeki terminal yükleme + mevcut sıralı fallback davranışını değiştirme.
  - Files/symbols: `GeneratorPlayer.nextEpisode`, `prevEpisode`, `showEpisodesOverlay`, `releasePlayer`, `startPlayer`
  - Depends on: T02, T03

- [x] **T06 — Skip ve kaynak hata failover davranışlarını çakışmasız sürdür**
  - Kullanıcı skip yaptığında dil uyumlu kaynak bekleme zorunluluğunu kaldır; oynatma hatasında `getNextLink()` sırasını koruyup eski kaynağa bağlı altyazıyı yeni kaynağa taşımadan mevcut otomatik altyazı zincirini yeniden çalıştır.
  - Files/symbols: `GeneratorPlayer.getNextLink`, `nextMirror`, `playerError`, `loadLink`, `overlayLoadingSkipButton`
  - Depends on: T03, T04
  - Invariants: Failover veya otomatik seçim `preferredSource`/`preferredSubtitle` açık kullanıcı tercihlerini yeniden yazmamalı.

- [ ] **T07 — Öncelik ve yarış durumları için regresyon testleri ekle**
  - Status: PARTIAL — Saf seçim politikası, kaynak önceliği ve kaynaksız/indirilen altyazı fallback testleri eklendi; oyuncu callback sırası, skip ve failover için cihaz/oynatıcı kapsamlı testler ayrı kaldı.
  - Türkçe ayarında `source1 + İngilizce`, `source2 + Türkçe` için source2; birden çok Türkçe kaynakta mevcut link sırası; kaynaksız/downloaded/embedded altyazıda eski fallback senaryolarını test et.
  - Açık kaynak tercihi ile dil uyumlu farklı kaynak çakışması, tercih edilen kaynak yokluğu, link/subtitle callback sırası, terminal yükleme, skip ve failover senaryolarını kapsa.
  - Files/symbols: `app/src/test/java/com/lagradost/cloudstream3/PlayerSelectionTest.kt`, gerekirse oyuncu/ViewModel testleri
  - Depends on: T01–T06

- [x] **T08 — Geliştirme kaydını güncelle**
  - Yeni öncelik zincirini, önceki bölüm-kaynak tercihi geliştirmesiyle etkileşimini, değişen dosyaları ve çalıştırılan doğrulamaları `docs/changelogs/` altında Türkçe olarak kaydet.
  - Files/symbols: `docs/changelogs/2026-08-24-episode-source-preference.md` referansı ve yeni geliştirme kaydı
  - Depends on: T07

## Validation

- [x] **V01 — `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew testPrereleaseDebugUnitTest` ile seçim ve oynatıcı birim testlerini çalıştır.**
- [x] **V02 — `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assemblePrereleaseDebug lintPrereleaseDebug` ile app derleme ve lint kontrolünü çalıştır.**
- [ ] **V03 — Telefon ve TV'de ilk açılış, ileri/geri/doğrudan bölüm geçişi, manuel kaynak seçimi, skip ve failover senaryolarını doğrula.**
  - Status: BLOCKED — Ortamda `adb` kurulu değil; bağlı telefon veya TV/emülatör üzerinde manuel doğrulama yapılamadı.
- [x] **V04 — Public/protected `library/` API'si değişirse `./gradlew library:checkKotlinAbi` çalıştır; bu planın varsayılanı app-içi model/politika değişikliğidir.**
  - Note: `library/` API'si değişmediği için ABI kontrolü gerekli değildi.

## Implementation Notes
- Kaynak bağlı altyazı eşleşmesinde `nameSuffix` veya callback sırası kullanılmamalı; yalnızca mevcut nullable `SubtitleData.source` ilişkisi güvenilir kabul edilmeli.
